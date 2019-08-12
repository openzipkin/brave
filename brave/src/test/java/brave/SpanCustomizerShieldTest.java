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
package brave;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SpanCustomizerShieldTest {
  Tracing tracing = Tracing.newBuilder().build();

  @Test public void doesNotStackOverflowOnToString() {
    Span span = tracing.tracer().newTrace();
    SpanCustomizerShield shield = new SpanCustomizerShield(span);
    assertThat(shield.toString())
      .isNotEmpty()
      .isEqualTo("SpanCustomizer(RealSpan(" + span.context().traceIdString() + "/" + span.context()
        .spanIdString() + "))");
  }
}
