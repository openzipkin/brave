/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.sampler;

/**
 * Returns true if this rule matches the input parameters
 *
 * <p>Implement {@link #hashCode()} and {@link #equals(Object)} if you want to replace existing
 * rules by something besides object identity.
 *
 * @see Matchers
 * @since 5.8
 */
public interface Matcher<P> {
  boolean matches(P parameters);
}
