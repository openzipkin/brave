package com.github.kristofa.brave.internal;

import com.github.kristofa.brave.Brave;
import com.twitter.zipkin.gen.Endpoint;

/**
 * Allows internal classes outside the package {@code com.github.kristofa.brave} to use non-public
 * methods. This allows us access internal methods while also making obvious the hooks are not for
 * public use. The only implementation of this interface is in {@link Brave}.
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.Internal}
 */
public abstract class Internal {

  public static void initializeInstanceForTests() {
    // Needed in tests to ensure that the instance is actually pointing to something.
    new Brave.Builder().build();
  }

  public abstract void setClientAddress(Brave brave, Endpoint ca);

  public static Internal instance;
}
