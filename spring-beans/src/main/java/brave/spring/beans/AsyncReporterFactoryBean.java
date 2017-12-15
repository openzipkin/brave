package brave.spring.beans;

import zipkin2.codec.SpanBytesEncoder;

/** @deprecated use {@link zipkin2.reporter.beans.AsyncReporterFactoryBean} */
@Deprecated
public class AsyncReporterFactoryBean extends zipkin2.reporter.beans.AsyncReporterFactoryBean {
  public AsyncReporterFactoryBean() {
    // This bean factory defaulted to the old format in the past. The new doesn't
    setEncoder(SpanBytesEncoder.JSON_V1);
  }
}
