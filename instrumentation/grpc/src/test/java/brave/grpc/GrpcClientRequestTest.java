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
package brave.grpc;

import io.grpc.Metadata;
import org.junit.Test;

import static brave.grpc.TestObjects.METHOD_DESCRIPTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GrpcClientRequestTest {
  Metadata metadata = new Metadata();
  GrpcClientRequest request = new GrpcClientRequest(METHOD_DESCRIPTOR);
  Metadata.Key<String> b3Key = AsciiMetadataKeyFactory.INSTANCE.create("b3");

  @Test public void metadata() {
    request.metadata(metadata).metadata(b3Key, "1");

    assertThat(metadata.get(b3Key))
      .isEqualTo("1");
  }

  @Test public void metadata_replace() {
    metadata.put(b3Key, "0");

    request.metadata(metadata).metadata(b3Key, "1");

    assertThat(request.metadata.get(b3Key))
      .isEqualTo("1");
  }

  @Test public void metadata_null() {
    assertThatThrownBy(() -> request.metadata(b3Key, "1")) // doesn't NPE
      .hasMessage("This code should never be called when null");
  }
}
