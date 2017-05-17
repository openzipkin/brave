package brave.cassandra.driver;

import brave.SpanCustomizer;
import brave.Tracer;
import brave.Tracing;
import brave.internal.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import cassandra.CassandraRule;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.internal.Util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assume.assumeTrue;

public class ITTracingSession {
  static {
    System.setProperty("cassandra.custom_tracing_class", CustomPayloadCaptor.class.getName());
  }

  @ClassRule public static CassandraRule cassandra = new CassandraRule();

  ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();

  Tracing tracing;
  CassandraClientTracing cassandraTracing;
  Cluster cluster;
  Session session;
  PreparedStatement prepared;

  @Before public void setup() throws IOException {
    tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();
    cluster = Cluster.builder()
        .addContactPointsWithPorts(Collections.singleton(cassandra.contactPoint()))
        .build();
    session = newSession();
    CustomPayloadCaptor.ref.set(null);
  }

  @After public void close() throws Exception {
    if (session != null) session.close();
    if (cluster != null) cluster.close();
    if (tracing != null) tracing.close();
  }

  Session newSession() {
    cassandraTracing = CassandraClientTracing.create(tracing);
    Session result = TracingSession.create(cassandraTracing, cluster.connect());
    prepared = result.prepare("SELECT * from system.schema_keyspaces");
    return result;
  }

  @Test public void makesChildOfCurrentSpan() throws Exception {
    brave.Span parent = tracing.tracer().newTrace().name("test").start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(parent)) {
      invokeBoundStatement();
    } finally {
      parent.finish();
    }

    assertThat(spans)
        .extracting(s -> tuple(s.traceId, s.parentId))
        .contains(tuple(parent.context().traceId(), parent.context().parentId()));
  }

  // CASSANDRA-12835 particularly is in 3.11, which fixes simple (non-bound) statement tracing
  @Test public void propagatesTraceIds_regularStatement() throws Exception {
    session.execute("SELECT * from system.schema_keyspaces");
    assertThat(CustomPayloadCaptor.ref.get())
        .isEmpty();
  }

  @Test public void propagatesTraceIds() throws Exception {
    invokeBoundStatement();

    assertThat(CustomPayloadCaptor.ref.get().keySet())
        .containsExactly("X-B3-SpanId", "X-B3-Sampled", "X-B3-TraceId");
  }

  @Test public void propagatesSampledFalse() throws Exception {
    tracing = tracingBuilder(Sampler.NEVER_SAMPLE).build();
    session.close();
    session = newSession();
    invokeBoundStatement();

    assertThat(CustomPayloadCaptor.ref.get().get("X-B3-Sampled"))
        .extracting(b -> string(b))
        .containsExactly("0");
  }

  @Test public void reportsClientAnnotationsToZipkin() throws Exception {
    invokeBoundStatement();

    assertThat(spans)
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly("cs", "cr");
  }

  @Test public void defaultSpanNameIsQuery() throws Exception {
    invokeBoundStatement();

    assertThat(spans)
        .extracting(s -> s.name)
        .containsExactly("bound-statement");
  }

  @Test public void reportsSpanOnTransportException() throws Exception {
    cluster.close();

    try {
      invokeBoundStatement();
      failBecauseExceptionWasNotThrown(NoHostAvailableException.class);
    } catch (NoHostAvailableException e) {
    }

    assertThat(spans).hasSize(1);
  }

  @Test public void addsErrorTag_onTransportException() throws Exception {
    reportsSpanOnTransportException();

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(a -> a.key.equals(Constants.ERROR))
        .extracting(a -> new String(a.value, Util.UTF_8))
        .containsExactly("All host(s) tried for query failed (no host was tried)");
  }

  @Test public void addsErrorTag_onCanceledFuture() throws Exception {
    ResultSetFuture resp = session.executeAsync("SELECT * from system.schema_keyspaces");
    assumeTrue("lost race on cancel", resp.cancel(true));

    close(); // blocks until the cancel finished

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(a -> a.key.equals(Constants.ERROR))
        .extracting(a -> new String(a.value, Util.UTF_8))
        .containsExactly("Task was cancelled.");
  }

  @Test public void reportsServerAddress() throws Exception {
    invokeBoundStatement();

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals(Constants.SERVER_ADDR))
        .extracting(b -> b.endpoint)
        .containsExactly(Endpoint.builder()
            .serviceName(cluster.getClusterName())
            .ipv4(127 << 24 | 1)
            .port(cassandra.contactPoint().getPort()).build()
        );
  }

  @Test public void customSampler() throws Exception {
    cassandraTracing = cassandraTracing.toBuilder()
        .sampler(CassandraClientSampler.NEVER_SAMPLE).build();
    session = TracingSession.create(cassandraTracing, ((TracingSession) session).delegate);

    invokeBoundStatement();

    assertThat(spans).isEmpty();
  }

  @Test public void supportsCustomization() throws Exception {
    cassandraTracing = cassandraTracing.toBuilder()
        .parser(new CassandraClientParser() {
          @Override public String spanName(Statement statement) {
            return "query";
          }

          @Override public void request(Statement statement, SpanCustomizer customizer) {
            super.request(statement, customizer);
            customizer.tag("cassandra.fetch_size", Integer.toString(statement.getFetchSize()));
          }

          @Override public void response(ResultSet resultSet, SpanCustomizer customizer) {
            customizer.tag("cassandra.available_without_fetching",
                Integer.toString(resultSet.getAvailableWithoutFetching()));
          }
        })
        .build().clientOf("remote-cluster");
    session = TracingSession.create(cassandraTracing, ((TracingSession) session).delegate);

    invokeBoundStatement();

    assertThat(spans)
        .extracting(s -> s.name)
        .containsExactly("query");

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals(Constants.SERVER_ADDR))
        .extracting(b -> b.endpoint.serviceName)
        .containsExactly("remote-cluster");
  }

  ResultSet invokeBoundStatement() {
    return session.execute(prepared.bind());
  }

  Tracing.Builder tracingBuilder(Sampler sampler) {
    return brave.Tracing.newBuilder()
        .currentTraceContext(new StrictCurrentTraceContext())
        .reporter(spans::add)
        .sampler(sampler);
  }

  static String string(ByteBuffer b) {
    return b != null ? Util.UTF_8.decode(b).toString() : null;
  }
}
