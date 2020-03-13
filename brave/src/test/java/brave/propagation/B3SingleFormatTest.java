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
package brave.propagation;

import brave.internal.Platform;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static brave.propagation.B3SingleFormat.parseB3SingleFormat;
import static brave.propagation.B3SingleFormat.writeB3SingleFormat;
import static brave.propagation.B3SingleFormat.writeB3SingleFormatAsBytes;
import static brave.propagation.B3SingleFormat.writeB3SingleFormatWithoutParentId;
import static brave.propagation.B3SingleFormat.writeB3SingleFormatWithoutParentIdAsBytes;
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
@PrepareForTest({Platform.class, B3SingleFormat.class})
public class B3SingleFormatTest {
  String traceIdHigh = "0000000000000009";
  String traceId = "0000000000000001";
  String parentId = "0000000000000002";
  String spanId = "0000000000000003";

  Platform platform = mock(Platform.class);

  @Before public void setupLogger() {
    mockStatic(Platform.class);
    when(Platform.get()).thenReturn(platform);
  }

  /** Either we asserted on the log messages or there weren't any */
  @After public void ensureNothingLogged() {
    verifyNoMoreInteractions(platform);
  }

  @Test public void writeB3SingleFormat_notYetSampled() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(3).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceId + "-" + spanId)
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test public void writeB3SingleFormat_notYetSampled_128() {
    TraceContext context = TraceContext.newBuilder().traceIdHigh(9).traceId(1).spanId(3).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceIdHigh + traceId + "-" + spanId)
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test public void writeB3SingleFormat_unsampled() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(3).sampled(false).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceId + "-" + spanId + "-0")
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test public void writeB3SingleFormat_sampled() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(3).sampled(true).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceId + "-" + spanId + "-1")
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test public void writeB3SingleFormat_debug() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(3).debug(true).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceId + "-" + spanId + "-d")
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test public void writeB3SingleFormat_parent() {
    TraceContext context =
      TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).sampled(true).build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceId + "-" + spanId + "-1-" + parentId)
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test public void writeB3SingleFormat_largest() {
    TraceContext context =
      TraceContext.newBuilder()
        .traceIdHigh(9)
        .traceId(1)
        .parentId(2)
        .spanId(3)
        .sampled(true)
        .build();

    assertThat(writeB3SingleFormat(context))
      .isEqualTo(traceIdHigh + traceId + "-" + spanId + "-1-" + parentId)
      .isEqualTo(new String(writeB3SingleFormatAsBytes(context), UTF_8));
  }

  @Test public void parseB3SingleFormat_largest() {
    assertThat(
      parseB3SingleFormat(traceIdHigh + traceId + "-" + spanId + "-1-" + parentId)
    ).extracting(TraceContextOrSamplingFlags::context).isEqualToComparingFieldByField(
      TraceContext.newBuilder()
        .traceIdHigh(9)
        .traceId(1)
        .parentId(2)
        .spanId(3)
        .sampled(true)
        .build()
    );
  }

  @Test public void writeB3SingleFormatWithoutParent_notYetSampled() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(3).build();

    assertThat(writeB3SingleFormatWithoutParentId(context))
      .isEqualTo(traceId + "-" + spanId)
      .isEqualTo(new String(writeB3SingleFormatWithoutParentIdAsBytes(context), UTF_8));
  }

  @Test public void writeB3SingleFormatWithoutParent_unsampled() {
    TraceContext context =
      TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).sampled(false).build();

    assertThat(writeB3SingleFormatWithoutParentId(context))
      .isEqualTo(traceId + "-" + spanId + "-0")
      .isEqualTo(new String(writeB3SingleFormatWithoutParentIdAsBytes(context), UTF_8));
  }

  @Test public void writeB3SingleFormatWithoutParent_sampled() {
    TraceContext context =
      TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).sampled(true).build();

    assertThat(writeB3SingleFormatWithoutParentId(context))
      .isEqualTo(traceId + "-" + spanId + "-1")
      .isEqualTo(new String(writeB3SingleFormatWithoutParentIdAsBytes(context), UTF_8));
  }

  @Test public void writeB3SingleFormatWithoutParent_debug() {
    TraceContext context =
      TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).debug(true).build();

    assertThat(writeB3SingleFormatWithoutParentId(context))
      .isEqualTo(traceId + "-" + spanId + "-d")
      .isEqualTo(new String(writeB3SingleFormatWithoutParentIdAsBytes(context), UTF_8));
  }

  /** for example, parsing a w3c context */
  @Test public void parseB3SingleFormat_middleOfString() {
    String input = "b3=" + traceId + traceId + "-" + spanId + ",";
    assertThat(parseB3SingleFormat(input, 3, input.length() - 1).context())
      .isEqualToComparingFieldByField(
        TraceContext.newBuilder().traceIdHigh(1).traceId(1).spanId(3).build()
      );
  }

  /** for example, parsing a w3c context */
  @Test public void parseB3SingleFormat_middleOfString_debugOnly() {
    String input = "b2=foo,b3=d,b4=bar";
    assertThat(parseB3SingleFormat(input, 10, 11).samplingFlags())
      .isSameAs(SamplingFlags.DEBUG);
  }

  @Test public void parseB3SingleFormat_middleOfString_incorrectIndex() {
    String input = "b2=foo,b3=d,b4=bar";
    assertThat(parseB3SingleFormat(input, 10, 12))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: only valid characters are lower-hex and hyphen", null);
  }

  @Test public void parseB3SingleFormat_spanIdsNotYetSampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId).context())
      .isEqualToComparingFieldByField(
        TraceContext.newBuilder().traceId(1).spanId(3).build()
      );
  }

  @Test public void parseB3SingleFormat_spanIdsNotYetSampled128() {
    assertThat(parseB3SingleFormat(traceId + traceId + "-" + spanId).context())
      .isEqualToComparingFieldByField(
        TraceContext.newBuilder().traceIdHigh(1).traceId(1).spanId(3).build()
      );
  }

  @Test public void parseB3SingleFormat_spanIdsUnsampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-0").context())
      .isEqualToComparingFieldByField(
        TraceContext.newBuilder().traceId(1).spanId(3).sampled(false).build()
      );
  }

  @Test public void parseB3SingleFormat_parent_unsampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-0-" + parentId).context())
      .isEqualToComparingFieldByField(
        TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).sampled(false).build()
      );
  }

  @Test public void parseB3SingleFormat_parent_debug() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-d-" + parentId).context())
      .isEqualToComparingFieldByField(
        TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).debug(true).build()
      );
  }

  // odd but possible to not yet sample a child
  @Test public void parseB3SingleFormat_parentid_notYetSampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-" + parentId).context())
      .isEqualToComparingFieldByField(
        TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).build()
      );
  }

  @Test public void parseB3SingleFormat_spanIdsWithDebug() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-d").context())
      .isEqualToComparingFieldByField(
        TraceContext.newBuilder().traceId(1).spanId(3).debug(true).build()
      );
  }

  @Test public void parseB3SingleFormat_sampledFalse() {
    assertThat(parseB3SingleFormat("0"))
      .isEqualTo(TraceContextOrSamplingFlags.NOT_SAMPLED);
  }

  @Test public void parseB3SingleFormat_sampled() {
    assertThat(parseB3SingleFormat("1"))
      .isEqualTo(TraceContextOrSamplingFlags.SAMPLED);
  }

  @Test public void parseB3SingleFormat_debug() {
    assertThat(parseB3SingleFormat("d"))
      .isEqualTo(TraceContextOrSamplingFlags.DEBUG);
  }

  /** This tests that the being index is inclusive and the end index is exclusive */
  @Test public void parseB3SingleFormat_ignoresBeforeAndAfter() {
    String encoded = traceId + "-" + spanId;
    String sequence = "??" + encoded + "??";
    assertThat(parseB3SingleFormat(sequence, 2, 2 + encoded.length()))
      .isEqualToComparingFieldByField(parseB3SingleFormat(encoded));
  }

  @Test public void parseB3SingleFormat_malformed() {
    assertThat(parseB3SingleFormat("not-a-tumor"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: only valid characters are lower-hex and hyphen", null);
  }

  @Test public void parseB3SingleFormat_malformed_notAscii() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-💩"))
      .isNull(); // instead of crashing

    verify(platform).log("Invalid input: only valid characters are lower-hex and hyphen", null);
  }

  @Test public void parseB3SingleFormat_malformed_uuid() {
    assertThat(parseB3SingleFormat("b970dafd-0d95-40aa-95d8-1d8725aebe40"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: more than 4 fields exist", null);
  }

  @Test public void parseB3SingleFormat_malformed_hyphenForSampled() {
    assertThat(parseB3SingleFormat("-")).isNull();

    verify(platform).log("Invalid input: expected 0, 1 or d for sampled", null);
  }

  @Test public void parseB3SingleFormat_too_many_fields() {
    assertThat(
      parseB3SingleFormat(traceId + "-" + spanId + "-1-" + parentId + "-"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: more than 4 fields exist", null);
  }

  @Test public void parseB3SingleFormat_sampledCorrupt() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-f"))
      .isNull(); // instead of crashing

    verify(platform).log("Invalid input: expected 0, 1 or d for sampled", null);
  }

  @Test public void parseB3SingleFormat_empty() {
    assertThat(parseB3SingleFormat("")).isNull();

    verify(platform).log("Invalid input: empty", null);
  }

  @Test public void parseB3SingleFormat_empty_traceId() {
    assertThat(parseB3SingleFormat("-234567812345678-" + spanId))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: expected a 16 or 32 lower hex trace ID", null);
  }

  @Test public void parseB3SingleFormat_empty_spanId() {
    assertThat(parseB3SingleFormat(traceId + "--"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: empty {0}", "span ID", null);
  }

  @Test public void parseB3SingleFormat_empty_spanId_with_parent() {
    assertThat(parseB3SingleFormat(traceId + "--" + parentId))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: empty {0}", "span ID", null);
  }

  /** We don't know if the intent was a sampled flag or a parent ID, but less logic to pick one. */
  @Test public void parseB3SingleFormat_empty_after_spanId() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: empty {0}", "sampled", null);
  }

  @Test public void parseB3SingleFormat_empty_sampled_with_parentId() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "--" + parentId))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: empty {0}", "sampled", null);
  }

  @Test public void parseB3SingleFormat_empty_parent_after_sampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-d-"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: empty {0}", "parent ID", null);
  }

  @Test public void parseB3SingleFormat_truncated_traceId() {
    assertThat(parseB3SingleFormat("1-" + spanId))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: expected a 16 or 32 lower hex trace ID", null);
  }

  @Test public void parseB3SingleFormat_truncated_traceId128() {
    assertThat(parseB3SingleFormat(traceIdHigh.substring(0, 15) + traceId + "-" + spanId))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: expected a 16 or 32 lower hex trace ID", null);
  }

  @Test public void parseB3SingleFormat_truncated_spanId() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId.substring(0, 15)))
      .isNull(); // instead of raising exception

    verify(platform).log("Truncated reading {0}", "span ID", null);
  }

  @Test public void parseB3SingleFormat_truncated_parentId() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-" + parentId.substring(0, 15)))
      .isNull(); // instead of raising exception

    verify(platform).log("Truncated reading {0}", "parent ID", null);
  }

  @Test public void parseB3SingleFormat_truncated_parentId_after_sampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-1-" + parentId.substring(0, 15)))
      .isNull(); // instead of raising exception

    verify(platform).log("Truncated reading {0}", "parent ID", null);
  }

  @Test public void parseB3SingleFormat_traceIdTooLong() {
    assertThat(parseB3SingleFormat(traceId + traceId + "a" + "-" + spanId))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: expected a 16 or 32 lower hex trace ID", null);
  }

  @Test public void parseB3SingleFormat_spanIdTooLong() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "a"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too long", "span ID", null);
  }

  /** Sampled too long without parent looks the same as a truncated parent ID */
  @Test public void parseB3SingleFormat_sampledTooLong() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-11-" + parentId))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too long", "sampled", null);
  }

  @Test public void parseB3SingleFormat_parentIdTooLong() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-" + parentId + "a"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too long", "parent ID", null);
  }

  @Test public void parseB3SingleFormat_parentIdTooLong_afterSampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-1-" + parentId + "a"))
      .isNull(); // instead of raising exception

    verify(platform).log("Invalid input: {0} is too long", "parent ID", null);
  }
}
