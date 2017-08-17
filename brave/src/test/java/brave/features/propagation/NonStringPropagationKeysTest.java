package brave.features.propagation;

import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import io.grpc.Metadata;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** This shows propagation keys don't need to be Strings. For example, we can propagate over gRPC */
public class NonStringPropagationKeysTest {
  Tracing tracing = Tracing.newBuilder().build();
  Propagation<Metadata.Key<String>> grpcPropagation = tracing.propagationFactory().create(
      name -> Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER)
  );
  TraceContext.Extractor<Metadata> extractor = grpcPropagation.extractor(Metadata::get);
  TraceContext.Injector<Metadata> injector = grpcPropagation.injector(Metadata::put);

  @After public void close() {
    tracing.close();
  }

  @Test
  public void injectExtractTraceContext() throws Exception {
    TraceContext context = tracing.tracer().newTrace().context();

    Metadata metadata = new Metadata();
    injector.inject(context, metadata);

    assertThat(metadata.keys())
        .containsExactly("x-b3-traceid", "x-b3-spanid", "x-b3-sampled");

    assertThat(extractor.extract(metadata).context())
        .isEqualTo(context);
  }
}
