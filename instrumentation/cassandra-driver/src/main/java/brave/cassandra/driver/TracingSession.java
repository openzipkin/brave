package brave.cassandra.driver;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import com.datastax.driver.core.AbstractSession;
import com.datastax.driver.core.CloseFuture;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import zipkin.Endpoint;

import static brave.Span.Kind.CLIENT;
import static zipkin.internal.Util.checkNotNull;

public final class TracingSession extends AbstractSession {
  public static Session create(Tracing tracing, Session delegate) {
    return new TracingSession(CassandraClientTracing.create(tracing), delegate);
  }

  public static Session create(CassandraClientTracing cassandraTracing, Session delegate) {
    return new TracingSession(cassandraTracing, delegate);
  }

  final Tracer tracer;
  final CassandraClientSampler sampler;
  final CassandraClientParser parser;
  final String remoteServiceName;
  final TraceContext.Injector<Map<String, ByteBuffer>> injector;
  final ProtocolVersion version;
  final Session delegate;

  TracingSession(CassandraClientTracing cassandraTracing, Session target) {
    checkNotNull(cassandraTracing, "cassandraTracing");
    this.delegate = checkNotNull(target, "delegate");
    tracer = cassandraTracing.tracing().tracer();
    sampler = cassandraTracing.sampler();
    parser = cassandraTracing.parser();
    String remoteServiceName = cassandraTracing.remoteServiceName();
    this.remoteServiceName = remoteServiceName != null
        ? remoteServiceName
        : target.getCluster().getClusterName();
    injector = cassandraTracing.tracing().propagation().injector((carrier, key, v) -> {
      int length = v.length(); // all values are ascii
      byte[] buf = new byte[length];
      for (int i = 0; i < length; i++) {
        buf[i] = (byte) v.charAt(i);
      }
      carrier.put(key, ByteBuffer.wrap(buf));
    });
    version = delegate.getCluster().getConfiguration().getProtocolOptions().getProtocolVersion();
  }

  @Override public ResultSetFuture executeAsync(Statement statement) {
    Span span = nextSpan(statement);
    if (!span.isNoop()) parser.request(statement, span.kind(CLIENT));

    // o.a.c.tracing.Tracing.newSession must use the same propagation format
    if (version.compareTo(ProtocolVersion.V4) >= 0) {
      statement.enableTracing();
      Map<String, ByteBuffer> payload = new LinkedHashMap<>();
      if (statement.getOutgoingPayload() != null) {
        payload.putAll(statement.getOutgoingPayload());
      }
      injector.inject(span.context(), payload);
      statement.setOutgoingPayload(payload);
    }

    span.start();
    ResultSetFuture result;
    try {
      result = delegate.executeAsync(statement);
    } catch (RuntimeException | Error e) {
      if (span.isNoop()) throw e;
      parser.error(e, span);
      span.finish();
      throw e;
    }
    Futures.addCallback(result, new FutureCallback<ResultSet>() {
      @Override public void onSuccess(ResultSet result) {
        if (span.isNoop()) return;
        InetSocketAddress host = result.getExecutionInfo().getQueriedHost().getSocketAddress();
        Endpoint.Builder remoteEndpoint = Endpoint.builder().serviceName(remoteServiceName);
        remoteEndpoint.parseIp(host.getAddress());
        remoteEndpoint.port(host.getPort());
        span.remoteEndpoint(remoteEndpoint.build());
        parser.response(result, span);
        span.finish();
      }

      @Override public void onFailure(Throwable e) {
        if (span.isNoop()) return;
        parser.error(e, span);
        span.finish();
      }
    });
    return result;
  }

  /** Creates a potentially noop span representing this request */
  Span nextSpan(Statement statement) {
    if (tracer.currentSpan() != null) return tracer.nextSpan();

    // If there was no parent, we are making a new trace. Try to sample the request.
    Boolean sampled = sampler.trySample(statement);
    if (sampled == null) return tracer.newTrace(); // defer sampling decision to trace ID
    return tracer.newTrace(sampled ? SamplingFlags.SAMPLED : SamplingFlags.NOT_SAMPLED);
  }

  @Override protected ListenableFuture<PreparedStatement> prepareAsync(String query,
      Map<String, ByteBuffer> customPayload) {
    SimpleStatement statement = new SimpleStatement(query);
    statement.setOutgoingPayload(customPayload);
    return prepareAsync(statement);
  }

  @Override public ListenableFuture<PreparedStatement> prepareAsync(String query) {
    return delegate.prepareAsync(query);
  }

  @Override public String getLoggedKeyspace() {
    return delegate.getLoggedKeyspace();
  }

  @Override public Session init() {
    return delegate.init();
  }

  @Override public ListenableFuture<Session> initAsync() {
    return delegate.initAsync();
  }

  @Override public ListenableFuture<PreparedStatement> prepareAsync(RegularStatement statement) {
    return delegate.prepareAsync(statement);
  }

  @Override public CloseFuture closeAsync() {
    return delegate.closeAsync();
  }

  @Override public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override public Cluster getCluster() {
    return delegate.getCluster();
  }

  @Override public State getState() {
    return delegate.getState();
  }
}
