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
package brave.internal;

public final class RecyclableBuffers {

  private static final ThreadLocal<char[]> ID_BUFFER = new ThreadLocal<>();

  /**
   * Returns a {@link ThreadLocal} reused {@code char[]} for use when decoding bytes into an ID hex
   * string. The buffer should be immediately copied into a {@link String} after decoding within the
   * same method.
   */
  public static char[] idBuffer() {
    char[] idBuffer = ID_BUFFER.get();
    if (idBuffer == null) {
      idBuffer = new char[32];
      ID_BUFFER.set(idBuffer);
    }
    return idBuffer;
  }

  private RecyclableBuffers() {
  }
}
