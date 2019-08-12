/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.features.propagation;

import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import io.grpc.Metadata;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** This shows propagation keys don't need to be Strings. For example, we can propagate over gRPC */
public class NonStringPropagationKeysTest {
  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
  Propagation<Metadata.Key<String>> grpcPropagation = B3Propagation.FACTORY.create(
    name -> Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER)
  );
  TraceContext.Extractor<Metadata> extractor = grpcPropagation.extractor(Metadata::get);
  TraceContext.Injector<Metadata> injector = grpcPropagation.injector(Metadata::put);

  @Test
  public void injectExtractTraceContext() {

    Metadata metadata = new Metadata();
    injector.inject(context, metadata);

    assertThat(metadata.keys())
      .containsExactly("x-b3-traceid", "x-b3-spanid", "x-b3-sampled");

    assertThat(extractor.extract(metadata).context())
      .isEqualTo(context);
  }
}
