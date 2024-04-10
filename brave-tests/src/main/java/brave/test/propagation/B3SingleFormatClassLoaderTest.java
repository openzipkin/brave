/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.test.propagation;

import brave.propagation.B3SingleFormat;
import org.junit.jupiter.api.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

class B3SingleFormatClassLoaderTest {
  @Test void unloadable_afterBasicUsage() {
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
