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
package brave.propagation.w3c;

import brave.internal.Platform;
import brave.propagation.TraceContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static brave.propagation.w3c.TraceparentFormat.parseTraceparentFormat;
import static brave.propagation.w3c.TraceparentFormat.writeTraceparentFormat;
import static brave.propagation.w3c.TraceparentFormat.writeTraceparentFormatAsBytes;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
// Added to declutter console: tells power mock not to mess with implicit classes we aren't testing
@PowerMockIgnore({"org.apache.logging.*", "javax.script.*"})
@PrepareForTest({Platform.class, TraceparentFormat.class})
public class TraceparentFormatTest {
  String traceIdHigh = "1234567890123459";
  String traceId = "1234567890123451";
  String parentId = "1234567890123452";
  String spanId = "1234567890123453";

  Platform platform = mock(Platform.class);

  @Before public void setupLogger() {
    mockStatic(Platform.class);
    when(Platform.get()).thenReturn(platform);
  }

  /** Either we asserted on the log messages or there weren't any */
  @After public void ensureNothingLogged() {
    verifyNoMoreInteractions(platform);
  }

  /** unsampled isn't the same as not-yet-sampled, but we have no better choice */
  @Test public void writeTraceparentFormat_notYetSampled_128() {
    TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16)).build();

    assertThat(writeTraceparentFormat(context))
      .isEqualTo("00-" + traceIdHigh + traceId + "-" + spanId + "-00")
      .isEqualTo(new String(writeTraceparentFormatAsBytes(context), UTF_8));
  }

  @Test public void writeTraceparentFormat_unsampled() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(false).build();

    assertThat(writeTraceparentFormat(context))
      .isEqualTo("00-0000000000000000" + traceId + "-" + spanId + "-00")
      .isEqualTo(new String(writeTraceparentFormatAsBytes(context), UTF_8));
  }

  @Test public void writeTraceparentFormat_sampled() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build();

    assertThat(writeTraceparentFormat(context))
      .isEqualTo("00-0000000000000000" + traceId + "-" + spanId + "-01")
      .isEqualTo(new String(writeTraceparentFormatAsBytes(context), UTF_8));
  }

  /** debug isn't the same as sampled, but we have no better choice */
  @Test public void writeTraceparentFormat_debug() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .debug(true).build();

    assertThat(writeTraceparentFormat(context))
      .isEqualTo("00-0000000000000000" + traceId + "-" + spanId + "-01")
      .isEqualTo(new String(writeTraceparentFormatAsBytes(context), UTF_8));
  }

  /**
   * There is no field for the parent ID in "traceparent" format. What it calls "parent ID" is
   * actually the span ID.
   */
  @Test public void writeTraceparentFormat_parent() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build();

    assertThat(writeTraceparentFormat(context))
      .isEqualTo("00-0000000000000000" + traceId + "-" + spanId + "-01")
      .isEqualTo(new String(writeTraceparentFormatAsBytes(context), UTF_8));
  }

  @Test public void writeTraceparentFormat_largest() {
    TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .debug(true).build();

    assertThat(writeTraceparentFormat(context))
      .isEqualTo("00-" + traceIdHigh + traceId + "-" + spanId + "-01")
      .isEqualTo(new String(writeTraceparentFormatAsBytes(context), UTF_8));
  }

  @Test public void parseTraceparentFormat_sampled() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + traceId + "-" + spanId + "-01"))
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16))
        .sampled(true).build()
      );
  }

  @Test public void parseTraceparentFormat_unsampled() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + traceId + "-" + spanId + "-00"))
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16))
        .sampled(false).build()
      );
  }

  @Test public void parseTraceparentFormat_padded() {
    assertThat(parseTraceparentFormat("00-0000000000000000" + traceId + "-" + spanId + "-01"))
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16))
        .sampled(true).build()
      );
  }

  @Test public void parseTraceparentFormat_padded_right() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + "0000000000000000-" + spanId + "-01"))
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16))
        .sampled(true).build()
      );
  }

  @Test public void parseTraceparentFormat_newer_version() {
    assertThat(parseTraceparentFormat("10-" + traceIdHigh + traceId + "-" + spanId + "-00"))
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16))
        .sampled(false).build()
      );
  }

  @Test public void parseTraceparentFormat_newer_version_ignores_extra_fields() {
    assertThat(parseTraceparentFormat("10-" + traceIdHigh + traceId + "-" + spanId + "-00-fobaly"))
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16))
        .sampled(false).build()
      );
  }

  @Test public void parseTraceparentFormat_newer_version_ignores_extra_flags() {
    assertThat(parseTraceparentFormat("10-" + traceIdHigh + traceId + "-" + spanId + "-ff"))
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16))
        .sampled(true).build()
      );
  }

  /** for example, parsing inside tracestate */
  @Test public void parseTraceparentFormat_middleOfString() {
    String input = "tc=00-" + traceIdHigh + traceId + "-" + spanId + "-01,";
    assertThat(parseTraceparentFormat(input, 3, input.length() - 1))
      .isEqualToComparingFieldByField(TraceContext.newBuilder()
        .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
        .traceId(Long.parseUnsignedLong(traceId, 16))
        .spanId(Long.parseUnsignedLong(spanId, 16))
        .sampled(true).build()
      );
  }

  @Test public void parseTraceparentFormat_middleOfString_incorrectIndex() {
    String input = "tc=00-" + traceIdHigh + traceId + "-" + spanId + "-00,";
    assertThat(parseTraceparentFormat(input, 0, 12))
      .isNull(); // instead of raising exception

    verify(platform)
      .log("Invalid input: only valid characters are lower-hex for {0}", "version", null);
  }

  /** This tests that the being index is inclusive and the end index is exclusive */
  @Test public void parseTraceparentFormat_ignoresBeforeAndAfter() {
    String encoded = "00-" + traceIdHigh + traceId + "-" + spanId + "-01";
    String sequence = "??" + encoded + "??";
    assertThat(parseTraceparentFormat(sequence, 2, 2 + encoded.length()))
      .isEqualToComparingFieldByField(parseTraceparentFormat(encoded));
  }

  @Test public void parseTraceparentFormat_malformed() {
    assertThat(parseTraceparentFormat("not-a-tumor"))
      .isNull(); // instead of raising exception

    verify(platform)
      .log("Invalid input: only valid characters are lower-hex for {0}", "version", null);
  }

  @Test public void parseTraceparentFormat_malformed_notAscii() {
    assertThat(parseTraceparentFormat(
      "00-" + traceIdHigh + traceId + "-" + spanId.substring(0, 15) + "💩-1"))
      .isNull(); // instead of crashing

    verify(platform)
      .log("Invalid input: only valid characters are lower-hex for {0}", "parent ID", null);
  }

  @Test public void parseTraceparentFormat_malformed_uuid() {
    assertThat(parseTraceparentFormat("b970dafd-0d95-40aa-95d8-1d8725aebe40"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too long", "version", null);
  }

  @Test public void parseTraceparentFormat_short_traceId() {
    assertThat(
      parseTraceparentFormat("00-" + traceId + "-" + spanId + "-01"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too short", "trace ID", null);
  }

  @Test public void parseTraceparentFormat_zero_traceId() {
    assertThat(
      parseTraceparentFormat("00-00000000000000000000000000000000-" + spanId + "-01"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: read all zeros {0}", "trace ID", null);
  }

  @Test public void parseTraceparentFormat_fails_on_extra_flags() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + traceId + "-" + spanId + "-ff"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: only choices are 00 or 01 {0}", "trace flags", null);
  }

  @Test public void parseTraceparentFormat_fails_on_extra_fields() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + traceId + "-" + spanId + "-0-"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too short", "trace flags", null);
  }

  @Test public void parseTraceparentFormat_fails_on_version_ff() {
    assertThat(parseTraceparentFormat("ff-" + traceIdHigh + traceId + "-" + spanId + "-01"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: ff {0}", "version", null);
  }

  @Test public void parseTraceparentFormat_zero_spanId() {
    assertThat(
      parseTraceparentFormat("00-" + traceIdHigh + traceId + "-0000000000000000-01"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: read all zeros {0}", "parent ID", null);
  }

  @Test public void parseTraceparentFormat_empty() {
    assertThat(parseTraceparentFormat("")).isNull();

    verify(platform).log("Invalid input: empty", null);
  }

  @Test public void parseTraceparentFormat_empty_version() {
    assertThat(parseTraceparentFormat("-" + traceIdHigh + traceId + "-" + spanId + "-00"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: empty {0}", "version", null);
  }

  @Test public void parseTraceparentFormat_empty_traceId() {
    assertThat(parseTraceparentFormat("00--" + spanId + "-00"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: empty {0}", "trace ID", null);
  }

  @Test public void parseTraceparentFormat_empty_spanId() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + traceId + "--01"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: empty {0}", "parent ID", null);
  }

  @Test public void parseTraceparentFormat_empty_flags() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + traceId + "-" + spanId + "-"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: empty {0}", "trace flags", null);
  }

  @Test public void parseTraceparentFormat_truncated_traceId() {
    assertThat(parseTraceparentFormat("00-1-" + spanId + "-01"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too short", "trace ID", null);
  }

  @Test public void parseTraceparentFormat_truncated_traceId128() {
    assertThat(parseTraceparentFormat("00-1" + traceId + "-" + spanId + "-01"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too short", "trace ID", null);
  }

  @Test public void parseTraceparentFormat_truncated_spanId() {
    assertThat(
      parseTraceparentFormat("00-" + traceIdHigh + traceId + "-" + spanId.substring(0, 15) + "-00"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too short", "parent ID", null);
  }

  @Test public void parseTraceparentFormat_truncated_flags() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + traceId + "-" + spanId + "-0"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too short", "trace flags", null);
  }

  @Test public void parseTraceparentFormat_traceIdTooLong() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + traceId + "a" + "-" + spanId + "-0"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too long", "trace ID", null);
  }

  @Test public void parseTraceparentFormat_spanIdTooLong() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + traceId + "-" + spanId + "a-0"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too long", "parent ID", null);
  }

  @Test public void parseTraceparentFormat_flagsTooLong() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + traceId + "-" + spanId + "-001"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: too long", null);
  }
}
