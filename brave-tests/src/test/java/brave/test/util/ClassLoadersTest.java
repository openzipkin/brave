package brave.test.util;

import java.lang.ref.WeakReference;
import java.util.logging.Logger;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoadersTest {
  static class Foo {
  }

  @Test public void createdNonDelegating_cantSeeCurrentClasspath() throws Exception {
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

  @Test public void assertRunIsUnloadable_threadLocalWithSystemClassIsUnloadable() {
    assertRunIsUnloadable(PresentThreadLocalWithSystemType.class, getClass().getClassLoader());
  }

  static class AbsentThreadLocalWithApplicationType implements Runnable {
    ThreadLocal<ClassLoadersTest> local = new ThreadLocal<>();

    @Override public void run() {
    }
  }

  @Test public void assertRunIsUnloadable_absentThreadLocalWithOurClassIsUnloadable() {
    assertRunIsUnloadable(AbsentThreadLocalWithApplicationType.class, getClass().getClassLoader());
  }

  static class PresentThreadLocalWithApplicationType implements Runnable {
    ThreadLocal<ClassLoadersTest> local = new ThreadLocal<>();

    @Override public void run() {
      local.set(new ClassLoadersTest());
    }
  }

  @Test(expected = AssertionError.class)
  public void assertRunIsUnloadable_threadLocalWithOurClassIsntUnloadable() {
    assertRunIsUnloadable(PresentThreadLocalWithApplicationType.class, getClass().getClassLoader());
  }

  static class PresentThreadLocalWithWeakRefToApplicationType implements Runnable {
    ThreadLocal<WeakReference<ClassLoadersTest>> local = new ThreadLocal<>();

    @Override public void run() {
      local.set(new WeakReference<>(new ClassLoadersTest()));
    }
  }

  @Test public void assertRunIsUnloadable_threadLocalWithWeakRefToOurClassIsUnloadable() {
    assertRunIsUnloadable(PresentThreadLocalWithWeakRefToApplicationType.class,
        getClass().getClassLoader());
  }

  /**
   * Mainly tests the internals of the assertion. Certain log managers can hold a reference to the
   * class that looked up a logger. Ensuring log manager implementation is out-of-scope. This
   * assertion is only here to avoid distraction of java logging interfering with class unloading.
   */
  @Test public void assertRunIsUnloadable_javaLoggerUnloadable() {
    assertRunIsUnloadable(JavaLogger.class, getClass().getClassLoader());
  }

  static class JavaLogger implements Runnable {
    final Logger javaLogger = Logger.getLogger(JavaLogger.class.getName());

    @Override public void run() {
      javaLogger.fine("foo");
    }
  }
}
