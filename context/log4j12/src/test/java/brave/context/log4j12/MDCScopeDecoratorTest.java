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
package brave.context.log4j12;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.apache.log4j.MDC;
import org.apache.log4j.helpers.Loader;
import org.junit.ComparisonFailure;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

public class MDCScopeDecoratorTest extends CurrentTraceContextTest {

  public MDCScopeDecoratorTest() {
    assumeMDCWorks();
  }

  /** {@link Loader#isJava1()} inteprets "java.version" of "11" as true (aka Java 1.1) */
  static void assumeMDCWorks() {
    String realJavaVersion = System.getProperty("java.version");
    try {
      System.setProperty("java.version", "1.8");
      MDC.put("foo", "bar");
      assumeThat(MDC.get("foo"))
        .withFailMessage("Couldn't verify MDC in general")
        .isEqualTo("bar");
    } finally {
      MDC.remove("foo");
      System.setProperty("java.version", realJavaVersion);
    }
  }

  @Override protected Class<? extends Supplier<CurrentTraceContext>> currentSupplier() {
    return CurrentSupplier.class;
  }

  static class CurrentSupplier implements Supplier<CurrentTraceContext> {
    @Override public CurrentTraceContext get() {
      return ThreadLocalCurrentTraceContext.newBuilder()
        .addScopeDecorator(MDCScopeDecorator.create())
        .build();
    }
  }

  @Test(expected = ComparisonFailure.class) // Log4J 1.2.x MDC is inheritable by default
  public void isnt_inheritable() throws Exception {
    super.isnt_inheritable();
  }

  @Override protected void verifyImplicitContext(@Nullable TraceContext context) {
    if (context != null) {
      assertThat(MDC.get("traceId"))
        .isEqualTo(context.traceIdString());
      assertThat(MDC.get("parentId"))
        .isEqualTo(context.parentIdString());
      assertThat(MDC.get("spanId"))
        .isEqualTo(context.spanIdString());
      assertThat(MDC.get("sampled"))
        .isEqualTo(context.sampled() != null ? context.sampled().toString() : null);
    } else {
      assertThat(MDC.get("traceId"))
        .isNull();
      assertThat(MDC.get("parentId"))
        .isNull();
      assertThat(MDC.get("spanId"))
        .isNull();
      assertThat(MDC.get("sampled"))
        .isNull();
    }
  }
}

