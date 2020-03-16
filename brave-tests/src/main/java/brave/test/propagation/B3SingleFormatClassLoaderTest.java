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
package brave.test.propagation;

import brave.propagation.B3SingleFormat;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class B3SingleFormatClassLoaderTest {
  @Test public void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      String expected = "1234567812345678-1234567812345678-1";
      String written =
        B3SingleFormat.writeB3SingleFormat(B3SingleFormat.parseB3SingleFormat(expected).context());
      if (!written.equals(expected)) throw new AssertionError();
    }
  }
}
