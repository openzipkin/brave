package brave.grpc;

import brave.propagation.Propagation;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;

enum AsciiMetadataKeyFactory implements Propagation.KeyFactory<Key<String>> {
  INSTANCE;

  @Override public Key<String> create(String name) {
    return Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
  }
}
