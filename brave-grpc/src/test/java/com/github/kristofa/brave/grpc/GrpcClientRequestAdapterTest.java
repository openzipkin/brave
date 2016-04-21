package com.github.kristofa.brave.grpc;

import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.grpc.BraveGrpcClientInterceptor.GrpcClientRequestAdapter;
import io.grpc.Metadata;
import io.grpc.examples.helloworld.GreeterGrpc;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Particularly, this demonstrates what metadata values look like for non-java developers. */
public class GrpcClientRequestAdapterTest {
  Metadata metadata = new Metadata();
  GrpcClientRequestAdapter adapter =
      new GrpcClientRequestAdapter(GreeterGrpc.METHOD_SAY_HELLO, metadata);

  @Test
  public void nullSpanIdMeansUnsampled() throws Exception {
    adapter.addSpanIdToRequest(null);

    assertThat(metadata.keys())
        .containsExactly("x-b3-sampled");

    assertThat(metadata.get(BravePropagationKeys.Sampled))
        .isEqualTo("0");
  }

  @Test
  public void sampled_rootSpan() throws Exception {
    adapter.addSpanIdToRequest(SpanId.create(1234L, 1234L, null));

    assertThat(metadata.keys())
        .containsExactly("x-b3-sampled", "x-b3-traceid", "x-b3-spanid");

    assertThat(metadata.get(BravePropagationKeys.Sampled))
        .isEqualTo("1");
    assertThat(metadata.get(BravePropagationKeys.TraceId))
        .isEqualTo("4d2");
    assertThat(metadata.get(BravePropagationKeys.SpanId))
        .isEqualTo("4d2");
  }

  @Test
  public void sampled_childSpan() throws Exception {
    adapter.addSpanIdToRequest(SpanId.create(1234L, 5678L, 1234L));

    assertThat(metadata.keys())
        .containsExactly("x-b3-sampled", "x-b3-traceid", "x-b3-spanid", "x-b3-parentspanid");

    assertThat(metadata.get(BravePropagationKeys.Sampled))
        .isEqualTo("1");
    assertThat(metadata.get(BravePropagationKeys.TraceId))
        .isEqualTo("4d2");
    assertThat(metadata.get(BravePropagationKeys.ParentSpanId))
        .isEqualTo("4d2");
    assertThat(metadata.get(BravePropagationKeys.SpanId))
        .isEqualTo("162e");
  }
}
