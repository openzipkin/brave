/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation;

import brave.internal.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static brave.propagation.B3SingleFormat.parseB3SingleFormat;
import static brave.propagation.B3SingleFormat.writeB3SingleFormat;
import static brave.propagation.B3SingleFormat.writeB3SingleFormatAsBytes;
import static brave.propagation.B3SingleFormat.writeB3SingleFormatWithoutParentId;
import static brave.propagation.B3SingleFormat.writeB3SingleFormatWithoutParentIdAsBytes;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class B3SingleFormatTest {
  String traceIdHigh = "1234567890123459";
  String traceId = "1234567890123451";
  String parentId = "1234567890123452";
  String spanId = "1234567890123453";
  Platform platform = mock(Platform.class);

  /** Either we asserted on the log messages or there weren't any */
  @AfterEach void ensureNothingLogged() {
    verifyNoMoreInteractions(platform);
  }

  @Test void writeB3SingleFormat_notYetSampled() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16)).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceId + "-" + spanId)
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test void writeB3SingleFormat_notYetSampled_128() {
    TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16)).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceIdHigh + traceId + "-" + spanId)
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test void writeB3SingleFormat_unsampled() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(false).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceId + "-" + spanId + "-0")
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test void writeB3SingleFormat_sampled() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceId + "-" + spanId + "-1")
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test void writeB3SingleFormat_debug() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .debug(true).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceId + "-" + spanId + "-d")
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test void writeB3SingleFormat_parent() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceId + "-" + spanId + "-1-" + parentId)
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test void writeB3SingleFormat_largest() {
    TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceIdHigh + traceId + "-" + spanId + "-1-" + parentId)
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test void parseB3SingleFormat_largest() {
    assertThat(
      parseB3SingleFormat(traceIdHigh + traceId + "-" + spanId + "-1-" + parentId).context()
    ).isEqualToComparingFieldByField(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  @Test void parseB3SingleFormat_padded() {
    assertThat(
      parseB3SingleFormat("0000000000000000" + traceId + "-" + spanId + "-1-" + parentId).context()
    ).isEqualToComparingFieldByField(TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  @Test void parseTraceparentFormat_padded_right() {
    assertThat(
      parseB3SingleFormat(traceIdHigh + "0000000000000000-" + spanId + "-1-" + parentId).context()
    ).isEqualToComparingFieldByField(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  @Test void writeB3SingleFormatWithoutParent_notYetSampled() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16)).build();

    assertThat(writeB3SingleFormatWithoutParentId(context))
      .isEqualTo(traceId + "-" + spanId)
      .isEqualTo(new String(writeB3SingleFormatWithoutParentIdAsBytes(context), UTF_8));
  }

  @Test void writeB3SingleFormatWithoutParent_unsampled() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(false).build();

    assertThat(writeB3SingleFormatWithoutParentId(context))
      .isEqualTo(traceId + "-" + spanId + "-0")
      .isEqualTo(new String(writeB3SingleFormatWithoutParentIdAsBytes(context), UTF_8));
  }

  @Test void writeB3SingleFormatWithoutParent_sampled() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build();

    assertThat(writeB3SingleFormatWithoutParentId(context))
      .isEqualTo(traceId + "-" + spanId + "-1")
      .isEqualTo(new String(writeB3SingleFormatWithoutParentIdAsBytes(context), UTF_8));
  }

  @Test void writeB3SingleFormatWithoutParent_debug() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .debug(true).build();

    assertThat(writeB3SingleFormatWithoutParentId(context))
      .isEqualTo(traceId + "-" + spanId + "-d")
      .isEqualTo(new String(writeB3SingleFormatWithoutParentIdAsBytes(context), UTF_8));
  }

  /** for example, parsing a w3c context */
  @Test void parseB3SingleFormat_middleOfString() {
    String input = "b3=" + traceIdHigh + traceId + "-" + spanId + ",";
    assertThat(parseB3SingleFormat(input, 3, input.length() - 1).context())
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16)).build()
      );
  }

  /** for example, parsing a w3c context */
  @Test void parseB3SingleFormat_middleOfString_debugOnly() {
    String input = "b2=foo,b3=d,b4=bar";
    assertThat(parseB3SingleFormat(input, 10, 11).samplingFlags())
      .isSameAs(SamplingFlags.DEBUG);
  }

  @Test void parseB3SingleFormat_middleOfString_incorrectIndex() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      String input = "b2=foo,b3=d,b4=bar";
      assertThat(parseB3SingleFormat(input, 10, 12))
        .isNull(); // instead of raising exception

      verify(platform)
        .log("Invalid input: only valid characters are lower-hex for {0}", "trace ID", null);
    }
  }

  @Test void parseB3SingleFormat_spanIdsNotYetSampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId).context())
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16)).build()
      );
  }

  @Test void parseB3SingleFormat_spanIdsNotYetSampled128() {
    assertThat(parseB3SingleFormat(traceIdHigh + traceId + "-" + spanId).context())
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16)).build()
      );
  }

  @Test void parseB3SingleFormat_spanIdsUnsampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-0").context())
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16))
        .sampled(false).build()
      );
  }

  @Test void parseB3SingleFormat_parent_unsampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-0-" + parentId).context())
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .parentId(Long.parseUnsignedLong(parentId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16))
        .sampled(false).build()
      );
  }

  @Test void parseB3SingleFormat_parent_debug() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-d-" + parentId).context())
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .parentId(Long.parseUnsignedLong(parentId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16))
        .debug(true).build()
      );
  }

  // odd but possible to not yet sample a child
  @Test void parseB3SingleFormat_parentid_notYetSampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-" + parentId).context())
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .parentId(Long.parseUnsignedLong(parentId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16)).build()
      );
  }

  @Test void parseB3SingleFormat_spanIdsWithDebug() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-d").context())
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16))
        .debug(true).build()
      );
  }

  @Test void parseB3SingleFormat_sampledFalse() {
    assertThat(parseB3SingleFormat("0"))
      .isEqualTo(TraceContextOrSamplingFlags.NOT_SAMPLED);
  }

  @Test void parseB3SingleFormat_sampled() {
    assertThat(parseB3SingleFormat("1"))
      .isEqualTo(TraceContextOrSamplingFlags.SAMPLED);
  }

  @Test void parseB3SingleFormat_debug() {
    assertThat(parseB3SingleFormat("d"))
      .isEqualTo(TraceContextOrSamplingFlags.DEBUG);
  }

  /** This tests that the being index is inclusive and the end index is exclusive */
  @Test void parseB3SingleFormat_ignoresBeforeAndAfter() {
    String encoded = traceId + "-" + spanId;
    String sequence = "??" + encoded + "??";
    assertThat(parseB3SingleFormat(sequence, 2, 2 + encoded.length()))
      .isEqualToComparingFieldByField(parseB3SingleFormat(encoded));
  }

  @Test void parseB3SingleFormat_malformed() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat("not-a-tumor"))
        .isNull(); // instead of raising exception

      verify(platform)
        .log("Invalid input: only valid characters are lower-hex for {0}", "trace ID", null);
    }
  }

  @Test void parseB3SingleFormat_malformed_notAscii() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "-" + spanId.substring(0, 15) + "💩"))
        .isNull(); // instead of crashing

      verify(platform)
        .log("Invalid input: only valid characters are lower-hex for {0}", "span ID", null);
    }
  }

  @Test void parseB3SingleFormat_malformed_uuid() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat("b970dafd-0d95-40aa-95d8-1d8725aebe40"))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: {0} is too short", "trace ID", null);
    }
  }

  @Test void parseB3SingleFormat_malformed_hyphenForSampled() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat("-")).isNull();

      verify(platform).log("Invalid input: expected 0, 1 or d for {0}", "sampled", null);
    }
  }

  @Test void parseB3SingleFormat_zero_traceId() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(
        parseB3SingleFormat("0000000000000000-" + spanId + "-1-" + parentId))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: read all zeros {0}", "trace ID", null);
    }
  }

  @Test void parseB3SingleFormat_zero_spanId() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(
        parseB3SingleFormat(traceId + "-0000000000000000-1-" + parentId))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: read all zeros {0}", "span ID", null);
    }
  }

  /** Serializing parent ID as zero is the same as none. */
  @Test void parseB3SingleFormat_zero_parentId() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-1-0000000000000000").context())
      .isEqualToComparingFieldByField(
        parseB3SingleFormat(traceId + "-" + spanId + "-1").context()
      );
  }

  @Test void parseB3SingleFormat_too_many_fields() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(
        parseB3SingleFormat(traceId + "-" + spanId + "-1-" + parentId + "-"))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: more than 4 fields exist", null);
    }
  }

  @Test void parseB3SingleFormat_sampledCorrupt() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-f"))
        .isNull(); // instead of crashing

      verify(platform).log("Invalid input: expected 0, 1 or d for {0}", "sampled", null);
    }
  }

  @Test void parseB3SingleFormat_empty() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat("")).isNull();

      verify(platform).log("Invalid input: empty", null);
    }
  }

  @Test void parseB3SingleFormat_empty_traceId() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat("-234567812345678-" + spanId))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: empty {0}", "trace ID", null);
    }
  }

  @Test void parseB3SingleFormat_empty_spanId() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "--"))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: empty {0}", "span ID", null);
    }
  }

  @Test void parseB3SingleFormat_empty_spanId_with_parent() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "--" + parentId))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: empty {0}", "span ID", null);
    }
  }

  /** We don't know if the intent was a sampled flag or a parent ID, but less logic to pick one. */
  @Test void parseB3SingleFormat_empty_after_spanId() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-"))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: empty {0}", "sampled", null);
    }
  }

  @Test void parseB3SingleFormat_empty_sampled_with_parentId() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "-" + spanId + "--" + parentId))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: empty {0}", "sampled", null);
    }
  }

  @Test void parseB3SingleFormat_empty_parent_after_sampled() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-d-"))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: empty {0}", "parent ID", null);
    }
  }

  @Test void parseB3SingleFormat_truncated_traceId() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat("1-" + spanId))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: {0} is too short", "trace ID", null);
    }
  }

  @Test void parseB3SingleFormat_truncated_traceId128() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceIdHigh.substring(0, 15) + traceId + "-" + spanId))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: {0} is too short", "trace ID", null);
    }
  }

  @Test void parseB3SingleFormat_truncated_spanId() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "-" + spanId.substring(0, 15)))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: {0} is too short", "span ID", null);
    }
  }

  @Test void parseB3SingleFormat_truncated_parentId() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-" + parentId.substring(0, 15)))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: {0} is too short", "parent ID", null);
    }
  }

  @Test void parseB3SingleFormat_truncated_parentId_after_sampled() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-1-" + parentId.substring(0, 15)))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: {0} is too short", "parent ID", null);
    }
  }

  @Test void parseB3SingleFormat_traceIdTooLong() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + traceId + "a" + "-" + spanId))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: {0} is too long", "trace ID", null);
    }
  }

  @Test void parseB3SingleFormat_spanIdTooLong() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "-" + spanId + "a"))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: {0} is too long", "span ID", null);
    }
  }

  /** Sampled too long without parent looks the same as a truncated parent ID */
  @Test void parseB3SingleFormat_sampledTooLong() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-11-" + parentId))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: {0} is too long", "sampled", null);
    }
  }

  @Test void parseB3SingleFormat_parentIdTooLong() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-" + parentId + "a"))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: {0} is too long", "parent ID", null);
    }
  }

  @Test void parseB3SingleFormat_parentIdTooLong_afterSampled() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-1-" + parentId + "a"))
        .isNull(); // instead of raising exception

      verify(platform).log("Invalid input: {0} is too long", "parent ID", null);
    }
  }
}
