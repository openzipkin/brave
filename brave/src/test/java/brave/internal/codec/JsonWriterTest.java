/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.codec;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonWriterTest {
  @Test void doesntStackOverflowOnToBufferWriterBug_lessThanBytes() {
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

  @Test void doesntStackOverflowOnToBufferWriterBug_Overflow() {
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
