/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.grpc;

import brave.baggage.BaggagePropagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** see {@link GrpcTracing.Builder#grpcPropagationFormatEnabled} for documentation. */
final class GrpcPropagation implements Propagation<String> {
  /**
   * This creates a compatible metadata key based on Census, except this extracts a brave trace
   * context as opposed to a census span context
   */
  static final Key<byte[]> GRPC_TRACE_BIN =
    Key.of("grpc-trace-bin", Metadata.BINARY_BYTE_MARSHALLER);
  /** This stashes the tag context in "extra" so it isn't lost */
  static final Key<TagsBin> GRPC_TAGS_BIN =
    Key.of("grpc-tags-bin", new Metadata.BinaryMarshaller<TagsBin>() {
      @Override public byte[] toBytes(TagsBin value) {
        return value.bytes;
      }

      @Override public TagsBin parseBytes(byte[] serialized) {
        if (serialized == null || serialized.length == 0) return null;
        return new TagsBin(serialized);
      }
    });

  /** Creates constant keys for use in propagating trace identifiers or baggage. */
  static Map<String, Key<String>> nameToKey(Propagation<String> propagation) {
    Map<String, Key<String>> result = new LinkedHashMap<>();
    for (String keyName : propagation.keys()) {
      result.put(keyName, Key.of(keyName, Metadata.ASCII_STRING_MARSHALLER));
    }
    for (String keyName : BaggagePropagation.allKeyNames(propagation)) {
      result.put(keyName, Key.of(keyName, Metadata.ASCII_STRING_MARSHALLER));
    }
    return result;
  }

  /** {@linkplain TraceContext#extra() extra field} that passes through bytes metdata. */
  static final class TagsBin {
    final byte[] bytes;

    TagsBin(byte[] bytes) {
      this.bytes = bytes;
    }
  }

  static Propagation<String> create(Propagation<String> delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    return new GrpcPropagation(delegate);
  }

  final Propagation<String> delegate;

  GrpcPropagation(Propagation<String> delegate) {
    this.delegate = delegate;
  }

  @Override public List<String> keys() {
    return delegate.keys();
  }

  @Override public <R> Injector<R> injector(Setter<R, String> setter) {
    return new GrpcInjector<>(this, setter);
  }

  @Override public <R> Extractor<R> extractor(Getter<R, String> getter) {
    return new GrpcExtractor<>(this, getter);
  }

  static final class GrpcInjector<R> implements Injector<R> {
    final Injector<R> delegate;
    final Setter<R, String> setter;

    GrpcInjector(GrpcPropagation propagation, Setter<R, String> setter) {
      this.delegate = propagation.delegate.injector(setter);
      this.setter = setter;
    }

    @Override public void inject(TraceContext context, R request) {
      if (request instanceof GrpcRequest) {
        byte[] serialized = TraceContextBinaryFormat.toBytes(context);
        Metadata metadata = ((GrpcRequest) request).headers();
        metadata.removeAll(GRPC_TRACE_BIN);
        metadata.put(GRPC_TRACE_BIN, serialized);
        TagsBin tags = context.findExtra(TagsBin.class);
        if (tags != null) {
          metadata.removeAll(GRPC_TAGS_BIN);
          metadata.put(GRPC_TAGS_BIN, tags);
        }
      }
      delegate.inject(context, request);
    }
  }

  static final class GrpcExtractor<R> implements Extractor<R> {
    final Extractor<R> delegate;
    final Propagation.Getter<R, String> getter;

    GrpcExtractor(GrpcPropagation propagation, Getter<R, String> getter) {
      this.delegate = propagation.delegate.extractor(getter);
      this.getter = getter;
    }

    @Override public TraceContextOrSamplingFlags extract(R request) {
      if (!(request instanceof GrpcRequest)) return delegate.extract(request);

      Metadata metadata = ((GrpcRequest) request).headers();

      // First, check if we are propagating gRPC tags.
      TagsBin tagsBin = metadata.get(GRPC_TAGS_BIN);

      // Next, check to see if there is a gRPC formatted trace context: use it if parsable.
      byte[] bytes = metadata.get(GRPC_TRACE_BIN);
      if (bytes != null) {
        TraceContext maybeContext = TraceContextBinaryFormat.parseBytes(bytes, tagsBin);
        if (maybeContext != null) return TraceContextOrSamplingFlags.create(maybeContext);
      }

      // Finally, try to extract an incoming, non-gRPC trace context. If tags exist, propagate them.
      TraceContextOrSamplingFlags result = delegate.extract(request);
      if (tagsBin == null) return result;
      return result.toBuilder().addExtra(tagsBin).build();
    }
  }
}
