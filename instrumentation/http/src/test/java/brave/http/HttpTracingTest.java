/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import org.junit.jupiter.api.Test;

import static brave.sampler.SamplerFunctions.deferDecision;
import static brave.sampler.SamplerFunctions.neverSample;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class HttpTracingTest {
  Tracing tracing = mock(Tracing.class);

  @Test void defaultSamplersDefer() {
    HttpTracing httpTracing = HttpTracing.newBuilder(tracing).build();

    assertThat(httpTracing.clientRequestSampler())
      .isSameAs(deferDecision());
    assertThat(httpTracing.serverRequestSampler())
      .isSameAs(deferDecision());
  }

  @Test void toBuilder() {
    HttpTracing httpTracing = HttpTracing.newBuilder(tracing).build();

    assertThat(httpTracing.toBuilder().build())
      .usingRecursiveComparison()
      .isEqualTo(httpTracing);

    assertThat(httpTracing.toBuilder().clientSampler(neverSample()).build())
      .usingRecursiveComparison()
      .isEqualTo(HttpTracing.newBuilder(tracing).clientSampler(neverSample()).build());
  }

  @Test void canOverridePropagation() {
    Propagation<String> propagation = B3Propagation.newFactoryBuilder()
      .injectFormat(B3Propagation.Format.SINGLE)
      .build().get();

    HttpTracing httpTracing = HttpTracing.newBuilder(tracing)
      .propagation(propagation)
      .build();

    assertThat(httpTracing.propagation())
      .isSameAs(propagation);

    httpTracing = httpTracing.create(tracing).toBuilder()
      .propagation(propagation)
      .build();

    assertThat(httpTracing.propagation())
      .isSameAs(propagation);
  }
}
