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
package brave.context.log4j12;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.apache.log4j.MDC;
import org.junit.Test;

import static brave.context.log4j12.MDCScopeDecoratorTest.assumeMDCWorks;
import static org.assertj.core.api.Assertions.assertThat;

public class MDCCurrentTraceContextTest extends CurrentTraceContextTest {
  public MDCCurrentTraceContextTest() {
    assumeMDCWorks();
  }

  @Override protected Class<? extends Supplier<CurrentTraceContext.Builder>> builderSupplier() {
    return BuilderSupplier.class;
  }

  static class BuilderSupplier implements Supplier<CurrentTraceContext.Builder> {
    @Override public CurrentTraceContext.Builder get() {
      return new MDCCurrentTraceContext.Builder(CurrentTraceContext.Default.inheritable());
    }
  }

  @Test(expected = AssertionError.class) // Log4J 1.2.x MDC is inheritable by default
  public void isnt_inheritable() throws Exception {
    super.isnt_inheritable();
  }

  @Test public void is_inheritable() throws Exception {
    super.is_inheritable(currentTraceContext);
  }

  @Override protected void verifyImplicitContext(@Nullable TraceContext context) {
    if (context != null) {
      assertThat(MDC.get("traceId")).isEqualTo(context.traceIdString());
      assertThat(MDC.get("spanId")).isEqualTo(context.spanIdString());
    } else {
      assertThat(MDC.get("traceId")).isNull();
      assertThat(MDC.get("spanId")).isNull();
    }
  }
}

