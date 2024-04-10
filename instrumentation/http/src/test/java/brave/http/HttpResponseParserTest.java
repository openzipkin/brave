/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import org.junit.jupiter.api.Test;

import static brave.http.HttpResponseParser.Default.catchAllName;
import static org.assertj.core.api.Assertions.assertThat;

public class HttpResponseParserTest {

  @Test void catchAllName_redirect() {
    assertThat(catchAllName("GET", 307))
      .isEqualTo("GET redirected"); // zipkin will implicitly lowercase this
  }

  @Test void routeBasedName_notFound() {
    assertThat(catchAllName("DELETE", 404))
      .isEqualTo("DELETE not_found"); // zipkin will implicitly lowercase this
  }

  @Test void notCatchAll() {
    assertThat(catchAllName("GET", 304))
      .isNull(); // not redirect
    assertThat(catchAllName("DELETE", 500))
      .isNull(); // not redirect or not found
  }
}
