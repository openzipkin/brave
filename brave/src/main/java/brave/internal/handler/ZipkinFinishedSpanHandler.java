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
package brave.internal.handler;

import brave.ErrorParser;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

/** logs exceptions instead of raising an error, as the supplied reporter could have bugs */
public final class ZipkinFinishedSpanHandler extends FinishedSpanHandler {
  final Reporter<zipkin2.Span> spanReporter;
  final MutableSpanConverter converter;

  public ZipkinFinishedSpanHandler(Reporter<zipkin2.Span> spanReporter,
    ErrorParser errorParser, String serviceName, String ip, int port) {
    this.spanReporter = spanReporter;
    this.converter = new MutableSpanConverter(errorParser, serviceName, ip, port);
  }

  /**
   * This is the last in the chain of finished span handlers. A predecessor may have set {@link
   * #alwaysSampleLocal()}, so we have to double-check here that the span was sampled to Zipkin.
   * Otherwise, we could accidentally send 100% data.
   */
  @Override public boolean handle(TraceContext context, MutableSpan span) {
    if (!Boolean.TRUE.equals(context.sampled())) return true;

    Span.Builder builderWithContextData = Span.newBuilder()
      .traceId(context.traceIdString())
      .parentId(context.parentIdString())
      .id(context.spanIdString());
    if (context.debug()) builderWithContextData.debug(true);

    converter.convert(span, builderWithContextData);
    spanReporter.report(builderWithContextData.build());
    return true;
  }

  @Override public boolean supportsOrphans() {
    return true;
  }

  @Override public String toString() {
    return spanReporter.toString();
  }
}
