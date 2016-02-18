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
  Endpoint web = Endpoint.create("zipkin-web", 172 << 24 | 17 << 16 | 3, 8080);

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
