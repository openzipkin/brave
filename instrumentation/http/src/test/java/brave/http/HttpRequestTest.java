/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.http;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpRequestTest {
  @Test public void toString_mentionsDelegate() {
    class IceCreamRequest extends HttpRequest {
      @Override public Object unwrap() {
        return "chocolate";
      }

      @Override public String method() {
        return null;
      }

      @Override public String path() {
        return null;
      }

      @Override public String url() {
        return null;
      }

      @Override public String header(String name) {
        return null;
      }
    }
    assertThat(new IceCreamRequest())
      .hasToString("IceCreamRequest{chocolate}");
  }

  @Test public void toString_doesntStackoverflowWhenUnwrapIsNull() {
    class BuggyRequest extends HttpRequest {
      @Override public Object unwrap() {
        return null;
      }

      @Override public String method() {
        return null;
      }

      @Override public String path() {
        return null;
      }

      @Override public String url() {
        return null;
      }

      @Override public String header(String name) {
        return null;
      }
    }
    assertThat(new BuggyRequest())
      .hasToString("BuggyRequest");
  }
}
