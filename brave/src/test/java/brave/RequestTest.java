/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTest {
  @Test void toString_mentionsDelegate() {
    class IceCreamRequest extends Request {
      @Override public Span.Kind spanKind() {
        return Span.Kind.SERVER;
      }

      @Override public Object unwrap() {
        return "chocolate";
      }
    }
    assertThat(new IceCreamRequest())
      .hasToString("IceCreamRequest{chocolate}");
  }

  @Test void toString_doesntStackoverflowWhenUnwrapIsThis() {
    class BuggyRequest extends Request {
      @Override public Object unwrap() {
        return this;
      }

      @Override public Span.Kind spanKind() {
        return Span.Kind.SERVER;
      }
    }
    assertThat(new BuggyRequest())
      .hasToString("BuggyRequest");
  }

  @Test void toString_doesntNPEWhenUnwrapIsNull() {
    class NoRequest extends Request {
      @Override public Object unwrap() {
        return null;
      }

      @Override public Span.Kind spanKind() {
        return Span.Kind.SERVER;
      }
    }
    assertThat(new NoRequest())
      .hasToString("NoRequest");
  }
}
