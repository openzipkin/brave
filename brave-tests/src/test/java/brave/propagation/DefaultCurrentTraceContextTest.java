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
package brave.propagation;

import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;

public class DefaultCurrentTraceContextTest extends CurrentTraceContextTest {

  @Override protected Class<? extends Supplier<CurrentTraceContext>> currentSupplier() {
    return CurrentSupplier.class;
  }

  static class CurrentSupplier implements Supplier<CurrentTraceContext> {
    @Override public CurrentTraceContext get() {
      return CurrentTraceContext.Default.create();
    }
  }

  @Test public void is_inheritable() throws Exception {
    super.is_inheritable(CurrentTraceContext.Default.inheritable());
  }

  @Before public void ensureNoOtherTestsTaint() {
    CurrentTraceContext.Default.INHERITABLE.set(null);
    CurrentTraceContext.Default.DEFAULT.set(null);
  }
}
