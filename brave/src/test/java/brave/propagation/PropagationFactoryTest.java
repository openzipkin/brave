/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
package brave.propagation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PropagationFactoryTest {
  Propagation.Factory factory = new Propagation.Factory() {
    @Override public Propagation<String> get() {
      return null;
    }
  };

  /** 64 bit trace IDs are not consistently mandatory across propagation, yet. */
  @Test void requires128BitTraceId_defaultsToFalse() {
    assertThat(factory.requires128BitTraceId())
      .isFalse();
  }

  /** join (reusing span ID on client and server side) is rarely supported outside B3. */
  @Test void supportsJoin_defaultsToFalse() {
    assertThat(B3Propagation.FACTORY.supportsJoin())
      .isTrue();
    assertThat(factory.supportsJoin())
      .isFalse();
  }

  @Test void decorate_defaultsToReturnSameInstance() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(1).build();
    assertThat(factory.decorate(context))
      .isSameAs(context);
  }
}
