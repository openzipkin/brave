package brave.cassandra;

import brave.Tracer;
import brave.cassandra.driver.TracingSession;
import brave.propagation.SamplingFlags;
import cassandra.CassandraRule;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import zipkin.Constants;
import zipkin.Span;
import zipkin.internal.MergeById;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracing {
  static {
    System.setProperty("cassandra.custom_tracing_class", Tracing.class.getName());
  }

  @ClassRule public static CassandraRule cassandra = new CassandraRule();

  ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();
  brave.Tracing tracing = brave.Tracing.newBuilder()
      .localServiceName("cassandra")
      .reporter(spans::add)
      .build();

  @After public void after() {
    tracing.close();
  }

  @Test public void doesntTraceWhenTracingDisabled() throws IOException {
    execute(session -> session
        .prepare("SELECT * from system.schema_keyspaces").bind());

    assertThat(spans).isEmpty();
  }

  @Test public void startsNewTraceWhenTracingEnabled() throws IOException {
    execute(session -> session
        .prepare("SELECT * from system.schema_keyspaces")
        .enableTracing().setOutgoingPayload(new LinkedHashMap<>()).bind());

    assertThat(spans).hasSize(1);
  }

  @Test public void startsNewTraceWhenTracingEnabled_noPayload() throws IOException {
    execute(session -> session
        .prepare("SELECT * from system.schema_keyspaces")
        .enableTracing().bind());

    assertThat(spans).hasSize(1);
  }

  @Test public void samplingDisabled() throws IOException {
    brave.Span unsampled = tracing.tracer().newTrace(SamplingFlags.NOT_SAMPLED);
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(unsampled)) {
      executeTraced(session -> session
          .prepare("SELECT * from system.schema_keyspaces").bind());
    }

    assertThat(spans).isEmpty();
  }

  @Test public void usesExistingTraceId() throws Exception {
    executeTraced(session -> session
        .prepare("SELECT * from system.schema_keyspaces").bind());

    assertThat(MergeById.apply(spans))
        .hasSize(1);

    assertThat(spans)
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsOnlyOnce("cs", "sr", "ss", "cr");
  }

  @Test public void reportsServerAnnotationsToZipkin() throws Exception {
    execute(session -> session
        .prepare("SELECT * from system.schema_keyspaces")
        .enableTracing().bind());

    assertThat(spans)
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsOnlyOnce("sr", "ss");
  }

  @Test public void defaultSpanNameIsType() throws Exception {
    execute(session -> session
        .prepare("SELECT * from system.schema_keyspaces")
        .enableTracing().bind());

    assertThat(spans)
        .extracting(s -> s.name)
        .containsExactly("query");
  }

  @Test public void defaultRequestTags() throws Exception {
    execute(session -> session
        .prepare("SELECT * from system.schema_keyspaces")
        .enableTracing().bind());

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .extracting(b -> b.key)
        .contains("cassandra.request", "cassandra.session_id");
  }

  @Test public void reportsClientAddress() throws Exception {
    execute(session -> session
        .prepare("SELECT * from system.schema_keyspaces")
        .enableTracing().bind());

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .extracting(b -> b.key)
        .contains(Constants.CLIENT_ADDR);
  }

  void execute(Function<Session, BoundStatement> statement) {
    try (Cluster cluster = Cluster.builder()
        .addContactPointsWithPorts(Collections.singleton(cassandra.contactPoint()))
        .build(); Session session = cluster.connect()) {
      session.execute(statement.apply(session));
    }
  }

  void executeTraced(Function<Session, Statement> statement) {
    try (Cluster cluster = Cluster.builder()
        .addContactPointsWithPorts(Collections.singleton(cassandra.contactPoint()))
        .build(); Session session = TracingSession.create(tracing, cluster.connect())) {
      session.execute(statement.apply(session));
    }
  }
}
