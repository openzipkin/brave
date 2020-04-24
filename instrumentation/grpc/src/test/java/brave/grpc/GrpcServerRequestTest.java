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
package brave.grpc;

import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import org.junit.Test;

import static brave.grpc.TestObjects.METHOD_DESCRIPTOR;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

public class GrpcServerRequestTest {
  Key<String> b3Key = Key.of("b3", Metadata.ASCII_STRING_MARSHALLER);
  GrpcServerRequest request =
    new GrpcServerRequest(singletonMap("b3", b3Key), METHOD_DESCRIPTOR, new Metadata());

  @Test public void metadata() {
    request.metadata.put(b3Key, "1");

    assertThat(request.getMetadata("b3"))
      .isEqualTo("1");
  }

  @Test public void metadata_null() {
    assertThat(request.getMetadata("b3")).isNull();
  }
}
