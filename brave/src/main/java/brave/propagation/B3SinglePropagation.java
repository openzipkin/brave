/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation;

import brave.Span;

/**
 * Implements the propagation format described in {@link B3SingleFormat}.
 *
 * <p>Use {@link B3Propagation#newFactoryBuilder()} to control inject formats.
 */
public final class B3SinglePropagation {
  public static final Propagation.Factory FACTORY = B3Propagation.newFactoryBuilder()
    .injectFormat(B3Propagation.Format.SINGLE)
    .injectFormat(Span.Kind.CLIENT, B3Propagation.Format.SINGLE)
    .injectFormat(Span.Kind.SERVER, B3Propagation.Format.SINGLE)
    .build();
}
