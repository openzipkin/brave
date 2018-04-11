package brave.grpc;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TagContextBinaryMarshallerTest {
  TagContextBinaryMarshaller binaryMarshaller = new TagContextBinaryMarshaller();

  @Test
  public void roundtrip() {
    Map<String, String> context = ImmutableMap.of("method", "foo");
    byte[] contextBytes = {
      0, // version
      0, // field number
      6, 'm', 'e', 't', 'h', 'o', 'd', //
      3, 'f', 'o', 'o' //
    };
    byte[] serialized = binaryMarshaller.toBytes(context);
    assertThat(serialized).containsExactly(contextBytes);

    assertThat(binaryMarshaller.parseBytes(serialized)).isEqualTo(context);
  }

  @Test
  public void roundtrip_multipleKeys() {
    Map<String, String> context = ImmutableMap.of("method", "foo", "user", "romeo");
    byte[] contextBytes = {
      0, // version
      0, // field number
      6, 'm', 'e', 't', 'h', 'o', 'd', //
      3, 'f', 'o', 'o', //
      0, // field number
      4, 'u', 's', 'e', 'r', //
      5, 'r', 'o', 'm', 'e', 'o' //
    };
    byte[] serialized = binaryMarshaller.toBytes(context);
    assertThat(serialized).containsExactly(contextBytes);

    assertThat(binaryMarshaller.parseBytes(serialized)).isEqualTo(context);
  }

  @Test
  public void parseBytes_empty() {
    assertThat(binaryMarshaller.parseBytes(new byte[0])).isEmpty();
  }

  @Test
  public void parseBytes_unsupportedVersionId_toEmpty() {
    byte[] contextBytes = {
      1, // unsupported version
      0, // field number
      6, 'm', 'e', 't', 'h', 'o', 'd', //
      3, 'f', 'o', 'o' //
    };
    assertThat(binaryMarshaller.parseBytes(contextBytes)).isNull();
  }

  @Test
  public void parseBytes_unsupportedFieldIdFirst_empty() {
    byte[] contextBytes = {
      0, // version
      1, // unsupported field number
      0, // field number
      6, 'm', 'e', 't', 'h', 'o', 'd', //
      3, 'f', 'o', 'o' //
    };
    assertThat(binaryMarshaller.parseBytes(contextBytes)).isEmpty();
  }

  @Test
  public void parseBytes_unsupportedFieldIdSecond_ignored() {
    byte[] contextBytes = {
      0, // version
      0, // field number
      6, 'm', 'e', 't', 'h', 'o', 'd', //
      3, 'f', 'o', 'o', //
      1, // unsupported field number
    };
    assertThat(binaryMarshaller.parseBytes(contextBytes))
        .isEqualTo(ImmutableMap.of("method", "foo"));
  }

  @Test
  public void parseBytes_truncatedDoesntCrash() {
    byte[] contextBytes = {
      0, // version
      0, // field number
      6, 'm', 'e', 't', // truncated
    };
    assertThat(binaryMarshaller.parseBytes(contextBytes)).isEmpty();
  }
}
