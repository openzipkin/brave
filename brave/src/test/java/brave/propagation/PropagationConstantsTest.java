/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation;

import brave.Tracing;
import org.junit.jupiter.api.Test;

/** Ensures there's no NPE when tracing builder uses defaults */
class PropagationConstantsTest {

  @Test void eagerReferencePropagationConstantPriorToUse() {
    Propagation<String> foo = Propagation.B3_STRING;
    Tracing.newBuilder().build().close();
  }
}
