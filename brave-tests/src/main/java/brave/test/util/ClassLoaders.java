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
package brave.test.util;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.LogManager;

import static org.assertj.core.api.Assertions.assertThat;

public final class ClassLoaders {

  /** Runs the assertion in a new classloader. Needed when you are creating parameterized tests */
  public static <T> void assertRunIsUnloadableWithSupplier(
    Class<? extends ConsumerRunnable<T>> assertion,
    Class<? extends Supplier<? extends T>> supplier
  ) {
    String property = assertion.getName() + ".supplier"; // assumes assertion is run only once
    System.setProperty(property, supplier.getName());
    try {
      assertRunIsUnloadable(assertion, assertion.getClassLoader());
    } finally {
      System.getProperties().remove(property);
    }
  }

  public static abstract class ConsumerRunnable<T> implements Runnable, Consumer<T> {
    Class<? extends Supplier<T>> subjectSupplier;

    protected ConsumerRunnable() {
      try {
        subjectSupplier =
          (Class) Class.forName(System.getProperty(getClass().getName() + ".supplier"));
      } catch (ClassNotFoundException e) {
        throw new AssertionError(e);
      }
    }

    @Override public void run() {
      accept(newInstance(subjectSupplier, getClass().getClassLoader()).get());
    }
  }

  /** Validating instance creator that ensures the supplier type is static or top-level */
  public static <T> T newInstance(Class<T> type, ClassLoader loader) {
    assertThat(type)
      .withFailMessage(type + " should be a static member class")
      .satisfies(c -> {
        assertThat(c.isLocalClass()).isFalse();
        assertThat(Modifier.isPublic(c.getModifiers())).isFalse();
      });

    try {
      Class<T> classToInstantiate = (Class<T>) loader.loadClass(type.getName());
      Constructor<T> ctor = classToInstantiate.getDeclaredConstructor();
      ctor.setAccessible(true);

      return ctor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Runs the type in a new classloader that recreates brave classes */
  public static void assertRunIsUnloadable(Class<? extends Runnable> runnable, ClassLoader parent) {
    // We can't use log4j2's log manager. More importantly, we want to make sure loggers don't hold
    // our test classloader from being collected.
    System.setProperty("java.util.logging.manager", LogManager.class.getName());
    assertThat(LogManager.getLogManager().getClass()).isSameAs(LogManager.class);

    WeakReference<ClassLoader> loader;
    try {
      loader = invokeRunFromNewClassLoader(runnable, parent);
    } catch (Exception e) {
      throw new AssertionError(e);
    }

    GarbageCollectors.blockOnGC();

    assertThat(loader.get())
      .withFailMessage(runnable + " includes state that couldn't be garbage collected")
      .isNull();
  }

  static WeakReference<ClassLoader> invokeRunFromNewClassLoader(
    Class<? extends Runnable> runnable, ClassLoader parent) throws Exception {

    ClassLoader loader = ClassLoaders.reloadClassNamePrefix(parent, "brave");
    Runnable instance = newInstance(runnable, loader);

    // ensure the classes are indeed different
    assertThat(instance.getClass()).isNotSameAs(runnable);

    Method run = instance.getClass().getMethod("run");
    run.setAccessible(true);

    run.invoke(instance);

    return new WeakReference<>(loader);
  }

  /**
   * Creates a new classloader that reloads types matching the given prefix. This is used to test
   * behavior such a leaked type in a thread local.
   *
   * <p>This works by using a bridge loader over the normal system one. The bridge loader always
   * loads new classes when they are prefixed by brave types.
   *
   * <p>This approach is the same as what's used in Android's {@code tests.util.ClassLoaderBuilder}
   * See https://android.googlesource.com/platform/libcore/+/master/support/src/test/java/tests/util/ClassLoaderBuilder.java
   */
  static ClassLoader reloadClassNamePrefix(ClassLoader parent, String prefix) {
    ClassLoader bridge = new ClassLoader(parent) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith(prefix)) throw new ClassNotFoundException("reloading type: " + name);
        return super.loadClass(name, resolve);
      }
    };
    try {
      return new URLClassLoader(classpathToUrls(), bridge);
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Couldn't read the current system classpath", e);
    }
  }

  static URL[] classpathToUrls() throws MalformedURLException {
    String[] classpath = System.getProperty("java.class.path").split(File.pathSeparator, -1);
    URL[] result = new URL[classpath.length];
    for (int i = 0; i < classpath.length; i++) {
      result[i] = new File(classpath[i]).toURI().toURL();
    }
    return result;
  }
}
