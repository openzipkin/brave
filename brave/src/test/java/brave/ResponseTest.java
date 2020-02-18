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
package brave;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResponseTest {
  @Test public void toString_mentionsDelegate() {
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

  @Test public void toString_doesntStackoverflowWhenUnwrapIsThis() {
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

  @Test public void toString_doesntNPEWhenUnwrapIsNull() {
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
