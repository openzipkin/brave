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
import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.Test;

import static brave.propagation.CurrentTraceContext.Default.INHERITABLE;

public class InheritableDefaultCurrentTraceContextTest extends CurrentTraceContextTest {
  @Override protected Class<? extends Supplier<CurrentTraceContext.Builder>> builderSupplier() {
    return BuilderSupplier.class;
  }

  static class BuilderSupplier implements Supplier<CurrentTraceContext.Builder> {
    @Override public CurrentTraceContext.Builder get() {
      return new ThreadLocalCurrentTraceContext.Builder(INHERITABLE);
    }
  }

  @Test(expected = AssertionError.class)
  public void isnt_inheritable() throws Exception {
    super.isnt_inheritable();
  }

  @Test public void is_inheritable() throws Exception {
    super.is_inheritable(currentTraceContext);
  }

  @Before public void ensureNoOtherTestsTaint() {
    INHERITABLE.set(null);
    CurrentTraceContext.Default.DEFAULT.set(null);
  }
}
