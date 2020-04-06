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

import brave.baggage.BaggageField;
import brave.internal.baggage.BaggageHandlers;
import brave.internal.baggage.ExtraBaggageFields;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import io.grpc.Metadata;
import java.util.List;

/** see {@link GrpcTracing.Builder#grpcPropagationFormatEnabled} for documentation. */
final class GrpcPropagation<K> implements Propagation<K> {
  static final BaggageField GRPC_TAGS_FIELD = BaggageField.create("grpc-tags");

  static final ExtraBaggageFields.Factory GRPC_TAGS_FACTORY =
    ExtraBaggageFields.newFactory(BaggageHandlers.string(GRPC_TAGS_FIELD));

  /**
   * This creates a compatible metadata key based on Census, except this extracts a brave trace
   * context as opposed to a census span context
   */
  static final Metadata.Key<byte[]> GRPC_TRACE_BIN =
    Metadata.Key.of("grpc-trace-bin", Metadata.BINARY_BYTE_MARSHALLER);
  /** This stashes the tag context in "extra" so it isn't lost */
  static final Metadata.Key<TagsBin> GRPC_TAGS_BIN =
    Metadata.Key.of("grpc-tags-bin", new Metadata.BinaryMarshaller<TagsBin>() {
      @Override public byte[] toBytes(TagsBin value) {
        return value.bytes;
      }

      @Override public TagsBin parseBytes(byte[] serialized) {
        if (serialized == null || serialized.length == 0) return null;
        return new TagsBin(serialized);
      }
    });

  /** {@linkplain TraceContext#extra() extra field} that passes through bytes metdata. */
  static final class TagsBin {
    final byte[] bytes;

    TagsBin(byte[] bytes) {
      this.bytes = bytes;
    }
  }

  static Propagation.Factory newFactory(Propagation.Factory delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    return new Factory(delegate);
  }

  static final class Factory extends Propagation.Factory {
    final Propagation.Factory delegate;

    Factory(Propagation.Factory delegate) {
      this.delegate = delegate;
    }

    @Override public boolean supportsJoin() {
      return false;
    }

    @Override public boolean requires128BitTraceId() {
      return true;
    }

    @Override public final <K> Propagation<K> create(KeyFactory<K> keyFactory) {
      return new GrpcPropagation<>(this, keyFactory);
    }

    @Override public TraceContext decorate(TraceContext context) {
      TraceContext result = delegate.decorate(context);
      return GRPC_TAGS_FACTORY.decorate(result);
    }
  }

  final Propagation<K> delegate;

  GrpcPropagation(Factory factory, KeyFactory<K> keyFactory) {
    this.delegate = factory.delegate.create(keyFactory);
  }

  @Override public List<K> keys() {
    return delegate.keys();
  }

  @Override public <C> Injector<C> injector(Setter<C, K> setter) {
    return new GrpcInjector<>(this, setter);
  }

  @Override public <C> Extractor<C> extractor(Getter<C, K> getter) {
    return new GrpcExtractor<>(this, getter);
  }

  static final class GrpcInjector<C, K> implements Injector<C> {
    final Injector<C> delegate;
    final Propagation.Setter<C, K> setter;

    GrpcInjector(GrpcPropagation<K> propagation, Setter<C, K> setter) {
      this.delegate = propagation.delegate.injector(setter);
      this.setter = setter;
    }

    @Override public void inject(TraceContext context, C carrier) {
      if (carrier instanceof GrpcClientRequest) {
        byte[] serialized = TraceContextBinaryFormat.toBytes(context);
        ((GrpcClientRequest) carrier).setMetadata(GRPC_TRACE_BIN, serialized);
        TagsBin tags = context.findExtra(TagsBin.class);
        if (tags != null) ((GrpcClientRequest) carrier).setMetadata(GRPC_TAGS_BIN, tags);
      }
      delegate.inject(context, carrier);
    }
  }

  static final class GrpcExtractor<C, K> implements Extractor<C> {
    final Extractor<C> delegate;
    final Propagation.Getter<C, K> getter;

    GrpcExtractor(GrpcPropagation<K> propagation, Getter<C, K> getter) {
      this.delegate = propagation.delegate.extractor(getter);
      this.getter = getter;
    }

    @Override public TraceContextOrSamplingFlags extract(C carrier) {
      if (!(carrier instanceof GrpcServerRequest)) return delegate.extract(carrier);

      GrpcServerRequest serverRequest = (GrpcServerRequest) carrier;

      // First, check if we are propagating gRPC tags.
      TagsBin tagsBin = serverRequest.getMetadata(GRPC_TAGS_BIN);

      // Next, check to see if there is a gRPC formatted trace context: use it if parsable.
      byte[] bytes = serverRequest.getMetadata(GRPC_TRACE_BIN);
      if (bytes != null) {
        TraceContext maybeContext = TraceContextBinaryFormat.parseBytes(bytes, tagsBin);
        if (maybeContext != null) return TraceContextOrSamplingFlags.create(maybeContext);
      }

      // Finally, try to extract an incoming, non-gRPC trace context. If tags exist, propagate them.
      TraceContextOrSamplingFlags result = delegate.extract(carrier);
      if (tagsBin == null) return result;
      return result.toBuilder().addExtra(tagsBin).build();
    }
  }
}
