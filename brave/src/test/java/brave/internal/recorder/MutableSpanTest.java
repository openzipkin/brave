package brave.internal.recorder;

import brave.Span;
import brave.Tracing;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import org.junit.Test;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;

import static brave.Span.Kind.CLIENT;
import static brave.Span.Kind.SERVER;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.CLIENT_ADDR;
import static zipkin.Constants.CLIENT_RECV;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.LOCAL_COMPONENT;
import static zipkin.Constants.SERVER_ADDR;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.Constants.SERVER_SEND;

public class MutableSpanTest {
  Endpoint localEndpoint = Platform.get().localEndpoint();
  TraceContext context = Tracing.newBuilder().build().tracer().newTrace().context();

  // zipkin needs one annotation or binary annotation so that the local endpoint can be read
  @Test public void addsDefaultBinaryAnnotation() {
    MutableSpan span = newSpan();

    span.start(1L);
    span.finish(2L);

    assertThat(span.toSpan().binaryAnnotations.get(0)).isEqualTo(
        BinaryAnnotation.create(LOCAL_COMPONENT, "", localEndpoint)
    );
  }

  @Test public void minimumDurationIsOne() {
    MutableSpan span = newSpan();

    span.start(1L).finish(1L);

    assertThat(span.toSpan().duration).isEqualTo(1L);
  }

  @Test public void doesntAddDefaultBinaryAnnotation() {
    MutableSpan span = newSpan();

    span.start(1L);
    span.annotate(2L, "foo"); // this has an endpoint, so don't add a default binary annotation
    span.finish(2L);

    assertThat(span.toSpan().binaryAnnotations).isEmpty();
  }

  @Test public void clientAnnotationsImplicitlySetKind() {
    for (String annotation : asList(Constants.CLIENT_SEND, Constants.CLIENT_RECV)) {
      assertThat(newSpan().annotate(1L, annotation).kind)
          .isEqualTo(Span.Kind.CLIENT);
    }
  }

  @Test public void serverAnnotationsImplicitlySetKind() {
    for (String annotation : asList(Constants.SERVER_RECV, Constants.SERVER_SEND)) {
      assertThat(newSpan().annotate(1L, annotation).kind)
          .isEqualTo(Span.Kind.SERVER);
    }
  }

  @Test public void whenKindIsClient_addsCsCr() {
    MutableSpan span = newSpan();

    span.kind(CLIENT);
    span.start(1L);
    span.finish(2L);

    assertThat(span.toSpan().annotations).extracting(a -> a.value)
        .containsExactly("cs", "cr");
  }

  @Test public void whenKindIsClient_addsCr() {
    MutableSpan span = newSpan();

    span.annotate(1L, CLIENT_SEND);
    span.finish(2L);

    assertThat(span.toSpan().annotations).extracting(a -> a.value)
        .containsExactly("cs", "cr");
  }

  @Test public void whenKindIsClient_addsCs() {
    MutableSpan span = newSpan();

    span.start(1L);
    span.annotate(2L, CLIENT_RECV);
    span.finish(null);

    assertThat(span.toSpan().annotations).extracting(a -> a.value)
        .containsExactly("cs", "cr");
  }

  @Test public void whenKindIsClient_addsSa() {
    MutableSpan span = newSpan();

    Endpoint endpoint = Endpoint.create("server", 127 | 1);
    span.kind(CLIENT);
    span.remoteEndpoint(endpoint);
    span.start(1L);
    span.finish(2L);

    assertThat(span.toSpan().binaryAnnotations.get(0)).isEqualTo(
        BinaryAnnotation.address(SERVER_ADDR, endpoint)
    );
  }

  // This prevents the server timestamp from overwriting the client one
  @Test public void doesntReportServerTimestampOnSharedSpans() {
    MutableSpan span = new MutableSpan(context.toBuilder().shared(true).build(), localEndpoint);

    span.start(1L);
    span.kind(SERVER);
    span.finish(2L);

    assertThat(span.toSpan()).extracting(s -> s.timestamp, s -> s.duration)
        .allSatisfy(u -> assertThat(u).isNull());
  }

  @Test public void whenKindIsServer_addsSrSs() {
    MutableSpan span = newSpan();

    span.kind(SERVER);
    span.start(1L);
    span.finish(1L);

    assertThat(span.toSpan().annotations).extracting(a -> a.value)
        .containsExactly("sr", "ss");
  }

  @Test public void whenKindIsServer_addsSs() {
    MutableSpan span = newSpan();

    span.annotate(1L, SERVER_RECV);
    span.finish(2L);

    assertThat(span.toSpan().annotations).extracting(a -> a.value)
        .containsExactly("sr", "ss");
  }

  @Test public void whenKindIsServer_addsSr() {
    MutableSpan span = newSpan();

    span.start(1L);
    span.annotate(2L, SERVER_SEND);
    span.finish(null);

    assertThat(span.toSpan().annotations).extracting(a -> a.value)
        .containsExactly("sr", "ss");
  }

  @Test public void whenKindIsServer_addsCa() {
    MutableSpan span = newSpan();

    Endpoint endpoint = Endpoint.create("caller", 127 | 1);
    span.kind(SERVER);
    span.remoteEndpoint(endpoint);
    span.start(1L);
    span.finish(2L);

    assertThat(span.toSpan().binaryAnnotations.get(0)).isEqualTo(
        BinaryAnnotation.address(CLIENT_ADDR, endpoint)
    );
  }

  @Test public void flushUnstartedNeitherSetsTimestampNorDuration() {
    zipkin.Span flushed = newSpan().finish(null).toSpan();
    assertThat(flushed).extracting(s -> s.timestamp, s -> s.duration)
        .allSatisfy(u -> assertThat(u).isNull());
  }

  /** We can't compute duration unless we started the span in the same tracer. */
  @Test public void finishUnstartedIsSameAsFlush() {
    assertThat(newSpan().finish(2L).toSpan())
        .isEqualTo(newSpan().finish(null).toSpan());
  }

  @Test public void oneWaySpan() {
    MutableSpan client = newSpan().kind(Span.Kind.CLIENT).start(1L).finish(null);

    assertThat(client.toSpan()).satisfies(s -> {
      assertThat(s.timestamp).isNull();
      assertThat(s.annotations).extracting(a -> a.value)
          .containsExactly("cs");
    });

    MutableSpan server = newSpan().kind(Span.Kind.SERVER).start(1L).finish(null);
    assertThat(server.toSpan()).satisfies(s -> {
      assertThat(s.timestamp).isNull();
      assertThat(s.annotations).extracting(a -> a.value)
          .containsExactly("sr");
    });
  }

  MutableSpan newSpan() {
    return new MutableSpan(context, localEndpoint);
  }
}
