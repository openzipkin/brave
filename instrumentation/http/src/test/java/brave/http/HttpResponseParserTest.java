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
package brave.http;

import org.junit.Test;

import static brave.http.HttpResponseParser.Default.catchAllName;
import static org.assertj.core.api.Assertions.assertThat;

public class HttpResponseParserTest {

  @Test public void catchAllName_redirect() {
    assertThat(catchAllName("GET", 307))
      .isEqualTo("GET redirected"); // zipkin will implicitly lowercase this
  }

  @Test public void routeBasedName_notFound() {
    assertThat(catchAllName("DELETE", 404))
      .isEqualTo("DELETE not_found"); // zipkin will implicitly lowercase this
  }

  @Test public void notCatchAll() {
    assertThat(catchAllName("GET", 304))
      .isNull(); // not redirect
    assertThat(catchAllName("DELETE", 500))
      .isNull(); // not redirect or not found
  }
}
