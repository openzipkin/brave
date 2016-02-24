package com.github.kristofa.brave.internal;

import java.nio.charset.Charset;

import static java.lang.String.format;

/**
 * Utilities, typically copied in from guava, so as to avoid dependency conflicts.
 */
public final class Util {

  public static final Charset UTF_8 = Charset.forName("UTF-8");

  /**
   * Copy of {@code com.google.common.base.Preconditions#checkNotNull}.
   */
  public static <T> T checkNotNull(T reference, String errorMessageTemplate, Object... errorMessageArgs) {
    if (reference == null) {
      // If either of these parameters is null, the right thing happens anyway
      throw new NullPointerException(
          format(errorMessageTemplate, errorMessageArgs));
    }
    return reference;
  }

  public static String checkNotBlank(String string, String errorMessageTemplate,
                                     Object... errorMessageArgs) {
    if (checkNotNull(string, errorMessageTemplate, errorMessageArgs).trim().isEmpty()) {
      throw new IllegalArgumentException(format(errorMessageTemplate, errorMessageArgs));
    }
    return string;
  }

  public static boolean equal(Object a, Object b) {
    return a == b || (a != null && a.equals(b));
  }

  private Util() { // no instances
  }
}
