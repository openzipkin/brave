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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HexBinaryMarshallerTest {
  HexBinaryMarshaller binaryMarshaller = new HexBinaryMarshaller();

  @Test
  public void roundtrip() {
    String contextHex = "0000066d6574686f6403666f6f";
    byte[] contextBytes = {
      0, // version
      0, // field number
      6, 'm', 'e', 't', 'h', 'o', 'd', //
      3, 'f', 'o', 'o' //
    };
    byte[] serialized = binaryMarshaller.toBytes(contextHex);
    assertThat(serialized).containsExactly(contextBytes);

    assertThat(binaryMarshaller.parseBytes(contextBytes)).isEqualTo(contextHex);
  }

  @Test
  public void roundtrip_multipleKeys() {
    String contextHex = "0000066d6574686f6403666f6f00047573657205726f6d656f";
    byte[] contextBytes = {
      0, // version
      0, // field number
      6, 'm', 'e', 't', 'h', 'o', 'd', //
      3, 'f', 'o', 'o', //
      0, // field number
      4, 'u', 's', 'e', 'r', //
      5, 'r', 'o', 'm', 'e', 'o' //
    };
    byte[] serialized = binaryMarshaller.toBytes(contextHex);
    assertThat(serialized).containsExactly(contextBytes);

    assertThat(binaryMarshaller.parseBytes(contextBytes)).isEqualTo(contextHex);
  }

  @Test
  public void parseBytes_null() {
    assertThat(binaryMarshaller.parseBytes(new byte[0])).isNull();
  }
}
