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
package brave.okhttp3;

import brave.okhttp3.TracingInterceptor.ResponseWrapper;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResponseWrapperTest {
  Response.Builder responseBuilder = new Response.Builder()
    .request(new Request.Builder().url("http://localhost/foo").build())
    .protocol(Protocol.HTTP_1_1);

  @Test public void statusCode() {
    Response response = responseBuilder.code(200).message("ok").build();

    assertThat(new ResponseWrapper(response, null).statusCode()).isEqualTo(200);
  }

  @Test public void statusCode_zero() {
    Response response = responseBuilder.code(0).message("ice cream!").build();

    assertThat(new ResponseWrapper(response, null).statusCode()).isZero();
  }
}
