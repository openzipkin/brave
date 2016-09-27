package com.github.kristofa.brave.internal;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import org.junit.Test;
import zipkin.Constants;

import static org.junit.Assert.assertEquals;

public class DefaultSpanCodecTest {

  Endpoint browser = Endpoint.create("browser-client", 1 << 24 | 2 << 16 | 3);
  Endpoint web = Endpoint.builder()
      .serviceName("web")
      .ipv4(124 << 24 | 13 << 16 | 90 << 8 | 3)
      // Cheat so we don't have to catch an exception here
      .ipv6(sun.net.util.IPAddressUtil.textToNumericFormatV6("2001:db8::c001"))
      .port(80).build();

  Span span = new Span() // browser calls web
      .setTrace_id(-692101025335252320L)
      .setName("get")
      .setId(-692101025335252320L)
      .setTimestamp(1444438900939000L)
      .setDuration(376000L)
      .addToAnnotations(Annotation.create(1444438900939000L, Constants.SERVER_RECV, web))
      .addToAnnotations(Annotation.create(1444438901315000L, Constants.SERVER_SEND, web))
      .addToBinary_annotations(BinaryAnnotation.address(Constants.CLIENT_ADDR, browser));

  @Test
  public void roundTripSpan_thrift() {
    byte[] encoded = DefaultSpanCodec.THRIFT.writeSpan(span);
    assertEquals(span, DefaultSpanCodec.THRIFT.readSpan(encoded));
  }

  @Test
  public void roundTripSpan_json() {
    byte[] encoded = DefaultSpanCodec.JSON.writeSpan(span);
    assertEquals(span, DefaultSpanCodec.JSON.readSpan(encoded));
  }
}
