package brave.grpc;

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import io.grpc.Metadata;
import java.util.Collections;

import static brave.grpc.TracingClientInterceptor.SETTER;

public class MetadataSetterTest extends PropagationSetterTest<Metadata, Metadata.Key<String>> {
  Metadata carrier = new Metadata();

  @Override public AsciiMetadataKeyFactory keyFactory() {
    return AsciiMetadataKeyFactory.INSTANCE;
  }

  @Override protected Metadata carrier() {
    return carrier;
  }

  @Override protected Propagation.Setter<Metadata, Metadata.Key<String>> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(Metadata carrier, Metadata.Key<String> key) {
    Iterable<String> result = carrier.getAll(key);
    return result != null ? result : Collections.emptyList();
  }
}
