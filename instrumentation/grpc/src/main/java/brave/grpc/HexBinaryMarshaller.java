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

import brave.internal.Platform;
import com.google.common.io.BaseEncoding;
import io.grpc.Metadata.BinaryMarshaller;

import static brave.internal.Throwables.propagateIfFatal;

/** This logs instead of throwing exceptions. */
final class HexBinaryMarshaller implements BinaryMarshaller<String> {
  // Guava 14+ is ok as gRPC already depends on it, and will for the foreseeable future
  static final BaseEncoding ENCODING = BaseEncoding.base16().lowerCase().omitPadding();

  @Override
  public byte[] toBytes(String encoded) {
    if (encoded == null) throw new NullPointerException("encoded == null"); // programming error
    try {
      return ENCODING.decode(encoded);
    } catch (Throwable e) {
      propagateIfFatal(e);
      Platform.get().log("error decoding gRPC tags", e);
      return null;
    }
  }

  @Override public String parseBytes(byte[] buf) {
    if (buf == null || buf.length == 0) return null;
    try {
      return ENCODING.encode(buf);
    } catch (Throwable e) {
      propagateIfFatal(e);
      Platform.get().log("error decoding gRPC tags", e);
      return null;
    }
  }
}
