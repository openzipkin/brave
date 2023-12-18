/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
package brave.test.util;

import java.lang.ref.WeakReference;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassLoadersTest {
  static class Foo {
  }

  @Test void createdNonDelegating_cantSeeCurrentClasspath() throws Exception {
    Foo foo = new Foo(); // load the class

    ClassLoader loader =
      ClassLoaders.reloadClassNamePrefix(getClass().getClassLoader(), getClass().getName());
    assertThat(loader.loadClass(Foo.class.getName()))
      .isNotSameAs(foo.getClass());
  }

  static class PresentThreadLocalWithSystemType implements Runnable {
    ThreadLocal<String> local = new ThreadLocal<>();

    @Override public void run() {
      local.set("foo");
    }
  }

  @Test void assertRunIsUnloadable_threadLocalWithSystemClassIsUnloadable() {
    assertRunIsUnloadable(PresentThreadLocalWithSystemType.class, getClass().getClassLoader());
  }

  static class AbsentThreadLocalWithApplicationType implements Runnable {
    ThreadLocal<ClassLoadersTest> local = new ThreadLocal<>();

    @Override public void run() {
    }
  }

  @Test void assertRunIsUnloadable_absentThreadLocalWithOurClassIsUnloadable() {
    assertRunIsUnloadable(AbsentThreadLocalWithApplicationType.class, getClass().getClassLoader());
  }

  static class PresentThreadLocalWithApplicationType implements Runnable {
    ThreadLocal<ClassLoadersTest> local = new ThreadLocal<>();

    @Override public void run() {
      local.set(new ClassLoadersTest());
    }
  }

  @Test void assertRunIsUnloadable_threadLocalWithOurClassIsntUnloadable() {
    assertThrows(AssertionError.class, () -> {
      assertRunIsUnloadable(PresentThreadLocalWithApplicationType.class, getClass().getClassLoader());
    });
  }

  static class PresentThreadLocalWithWeakRefToApplicationType implements Runnable {
    ThreadLocal<WeakReference<ClassLoadersTest>> local = new ThreadLocal<>();

    @Override public void run() {
      local.set(new WeakReference<>(new ClassLoadersTest()));
    }
  }

  @Test void assertRunIsUnloadable_threadLocalWithWeakRefToOurClassIsUnloadable() {
    assertRunIsUnloadable(PresentThreadLocalWithWeakRefToApplicationType.class,
      getClass().getClassLoader());
  }

  /**
   * Mainly tests the internals of the assertion. Certain log managers can hold a reference to the
   * class that looked up a logger. Ensuring log manager implementation is out-of-scope. This
   * assertion is only here to avoid distraction of java logging interfering with class unloading.
   */
  @Test void assertRunIsUnloadable_javaLoggerUnloadable() {
    assertRunIsUnloadable(JavaLogger.class, getClass().getClassLoader());
  }

  static class JavaLogger implements Runnable {
    final Logger javaLogger = Logger.getLogger(JavaLogger.class.getName());

    @Override public void run() {
      javaLogger.fine("foo");
    }
  }
}
