/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseTest {
  @Test void toString_mentionsDelegate() {
    class IceCreamResponse extends Response {
      @Override public Span.Kind spanKind() {
        return Span.Kind.SERVER;
      }

      @Override public Throwable error() {
        return null;
      }

      @Override public Object unwrap() {
        return "chocolate";
      }
    }
    assertThat(new IceCreamResponse())
      .hasToString("IceCreamResponse{chocolate}");
  }

  @Test void toString_doesntStackoverflowWhenUnwrapIsThis() {
    class BuggyResponse extends Response {
      @Override public Object unwrap() {
        return this;
      }

      @Override public Throwable error() {
        return null;
      }

      @Override public Span.Kind spanKind() {
        return Span.Kind.SERVER;
      }
    }
    assertThat(new BuggyResponse())
      .hasToString("BuggyResponse");
  }

  @Test void toString_doesntNPEWhenUnwrapIsNull() {
    class NoResponse extends Response {
      @Override public Object unwrap() {
        return null;
      }

      @Override public Throwable error() {
        return null;
      }

      @Override public Span.Kind spanKind() {
        return Span.Kind.SERVER;
      }
    }
    assertThat(new NoResponse())
      .hasToString("NoResponse");
  }
}
