package com.github.kristofa.brave.grpc;

import com.github.kristofa.brave.Propagation;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;

enum AsciiMetadataKeyFactory implements Propagation.KeyFactory<Key<String>> {
  INSTANCE;

  @Override public Key<String> create(String name) {
    return Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
  }
}
