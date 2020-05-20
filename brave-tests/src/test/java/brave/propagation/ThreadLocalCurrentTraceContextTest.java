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
package brave.propagation;

import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadLocalCurrentTraceContextTest extends CurrentTraceContextTest {
  @Override protected Class<? extends Supplier<CurrentTraceContext.Builder>> builderSupplier() {
    return BuilderSupplier.class;
  }

  /** Since the default thread-local is static, this helps code avoid leaks made by others. */
  @Test public void clear_unleaks() {
    currentTraceContext.newScope(context); // leak a scope

    assertThat(currentTraceContext.get()).isEqualTo(context);

    ((ThreadLocalCurrentTraceContext) currentTraceContext).clear();

    assertThat(currentTraceContext.get()).isNull();
  }

  static class BuilderSupplier implements Supplier<CurrentTraceContext.Builder> {
    @Override public CurrentTraceContext.Builder get() {
      return ThreadLocalCurrentTraceContext.newBuilder();
    }
  }
}
