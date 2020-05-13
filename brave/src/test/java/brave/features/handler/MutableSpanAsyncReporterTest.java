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
package brave.features.handler;

import brave.Tags;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.MutableSpanBytesEncoder;
import brave.handler.SpanHandler;
import brave.propagation.B3SingleFormat;
import brave.propagation.TraceContext;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import zipkin2.codec.BytesEncoder;
import zipkin2.codec.Encoding;
import zipkin2.junit.ZipkinRule;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.okhttp3.OkHttpSender;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is an example of why {@link MutableSpanBytesEncoder} was written. Particularly, it allows
 * direct encoding from {@link MutableSpan} into JSON without converting to Zipkin model first.
 */
public class MutableSpanAsyncReporterTest {
  @Rule public ZipkinRule zipkin = new ZipkinRule();

  MutableSpanBytesEncoder mutableSpanBytesEncoder = MutableSpanBytesEncoder.zipkinJsonV2(Tags.ERROR);
  BytesEncoder<MutableSpan> zipkinBytesEncoderAdapter = new BytesEncoder<MutableSpan>() {

    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public int sizeInBytes(MutableSpan span) {
      return mutableSpanBytesEncoder.sizeInBytes(span);
    }

    @Override public byte[] encode(MutableSpan span) {
      return mutableSpanBytesEncoder.encode(span);
    }

    @Override public byte[] encodeList(List<MutableSpan> spans) {
      return mutableSpanBytesEncoder.encodeList(spans);
    }
  };

  OkHttpSender sender = OkHttpSender.create(zipkin.httpUrl() + "/api/v2/spans");

  AsyncReporter<MutableSpan> reporter = AsyncReporter.builder(sender)
      .messageTimeout(0, TimeUnit.MILLISECONDS) // don't spawn a thread
      .build(zipkinBytesEncoderAdapter);

  SpanHandler spanHandlerAdapter = new SpanHandler() {
    @Override public boolean end(TraceContext context, MutableSpan span, SpanHandler.Cause cause) {
      if (!Boolean.TRUE.equals(context.sampled())) return true;
      reporter.report(span);
      return true;
    }
  };

  Tracing tracing = Tracing.newBuilder()
      .localServiceName("Aa")
      .localIp("1.2.3.4")
      .localPort(80)
      .addSpanHandler(spanHandlerAdapter)
      .build();

  @After public void close() {
    tracing.close();
    reporter.close();
    sender.close();
  }

  /** This mainly shows endpoints are taken from Brave, and error is back-filled. */
  @Test public void basicSpan() {
    TraceContext context = B3SingleFormat.parseB3SingleFormat(
        "50d980fffa300f29-86154a4ba6e91385-1"
    ).context();

    tracing.tracer().toSpan(context).name("test")
        .start(1L)
        .error(new RuntimeException("this cake is a lie"))
        .finish(3L);

    reporter.flush();

    assertThat(zipkin.getTraces()).hasSize(1).first().hasToString(
        "[{\"traceId\":\"50d980fffa300f29\","
            + "\"id\":\"86154a4ba6e91385\","
            + "\"name\":\"test\","
            + "\"timestamp\":1,"
            + "\"duration\":2,"
            + "\"localEndpoint\":{"
            + "\"serviceName\":\"aa\","
            + "\"ipv4\":\"1.2.3.4\","
            + "\"port\":80},"
            + "\"tags\":{\"error\":\"this cake is a lie\"}}]"
    );
  }
}
