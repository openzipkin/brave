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
package brave.internal.codec;

import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JsonWriterTest {
  @Test public void doesntStackOverflowOnToBufferWriterBug_lessThanBytes() {
    class FooWriter implements WriteBuffer.Writer<Object> {
      @Override public int sizeInBytes(Object value) {
        return 2;
      }

      @Override public void write(Object value, WriteBuffer buffer) {
        buffer.writeByte('a');
        throw new RuntimeException("buggy");
      }
    }

    class Foo {
      @Override public String toString() {
        return new String(JsonWriter.write(new FooWriter(), this), UTF_8);
      }
    }

    Foo foo = new Foo();
    assertThatThrownBy(foo::toString)
        .isInstanceOf(AssertionError.class)
        .hasMessage("Bug found using FooWriter to write Foo as json. Wrote 1/2 bytes: a");
  }

  @Test public void doesntStackOverflowOnToBufferWriterBug_Overflow() {
    // pretend there was a bug calculating size, ex it calculated incorrectly as to small
    class FooWriter implements WriteBuffer.Writer {
      @Override public int sizeInBytes(Object value) {
        return 2;
      }

      @Override public void write(Object value, WriteBuffer buffer) {
        buffer.writeByte('a');
        buffer.writeByte('b');
        buffer.writeByte('c'); // wrote larger than size!
      }
    }

    class Foo {
      @Override public String toString() {
        return new String(JsonWriter.write(new FooWriter(), this), UTF_8);
      }
    }

    Foo foo = new Foo();
    assertThatThrownBy(foo::toString)
        .isInstanceOf(AssertionError.class)
        .hasMessage("Bug found using FooWriter to write Foo as json. Wrote 2/2 bytes: ab");
  }
}
