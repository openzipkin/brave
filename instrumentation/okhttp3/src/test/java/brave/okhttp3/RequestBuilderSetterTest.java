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
package brave.okhttp3;

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import okhttp3.Request;

import static brave.okhttp3.TracingInterceptor.SETTER;

public class RequestBuilderSetterTest extends PropagationSetterTest<Request.Builder, String> {
  Request.Builder carrier = new Request.Builder().url("http://foo.com");

  @Override public Propagation.KeyFactory<String> keyFactory() {
    return Propagation.KeyFactory.STRING;
  }

  @Override protected Request.Builder carrier() {
    return carrier;
  }

  @Override protected Propagation.Setter<Request.Builder, String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(Request.Builder carrier, String key) {
    return carrier.build().headers(key);
  }
}
