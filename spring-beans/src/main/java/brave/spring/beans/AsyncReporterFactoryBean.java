package brave.spring.beans;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.ReporterMetrics;
import zipkin.reporter.Sender;
import zipkin.reporter.SpanEncoder;

/** Spring XML config does not support chained builders. This converts accordingly */
public class AsyncReporterFactoryBean extends AbstractFactoryBean<AsyncReporter> {
  Sender sender;
  SpanEncoder encoder = SpanEncoder.JSON_V1;
  ReporterMetrics metrics;
  Integer messageMaxBytes;
  Integer messageTimeout;
  Integer closeTimeout;
  Integer queuedMaxSpans;
  Integer queuedMaxBytes;

  @Override public Class<? extends AsyncReporter> getObjectType() {
    return AsyncReporter.class;
  }

  @Override protected AsyncReporter createInstance() throws Exception {
    AsyncReporter.Builder builder = AsyncReporter.builder(sender);
    if (metrics != null) builder.metrics(metrics);
    if (messageMaxBytes != null) builder.messageMaxBytes(messageMaxBytes);
    if (messageTimeout != null) builder.messageTimeout(messageTimeout, TimeUnit.MILLISECONDS);
    if (closeTimeout != null) builder.closeTimeout(closeTimeout, TimeUnit.MILLISECONDS);
    if (queuedMaxSpans != null) builder.queuedMaxSpans(queuedMaxSpans);
    if (queuedMaxBytes != null) builder.queuedMaxBytes(queuedMaxBytes);
    return builder.build(encoder);
  }

  @Override protected void destroyInstance(AsyncReporter instance) throws Exception {
    instance.close();
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setSender(Sender sender) {
    this.sender = sender;
  }

  public void setEncoder(SpanEncoder encoder) {
    this.encoder = encoder;
  }

  public void setMetrics(ReporterMetrics metrics) {
    this.metrics = metrics;
  }

  public void setMessageMaxBytes(Integer messageMaxBytes) {
    this.messageMaxBytes = messageMaxBytes;
  }

  public void setMessageTimeout(Integer messageTimeout) {
    this.messageTimeout = messageTimeout;
  }

  public void setCloseTimeout(Integer closeTimeout) {
    this.closeTimeout = closeTimeout;
  }

  public void setQueuedMaxSpans(Integer queuedMaxSpans) {
    this.queuedMaxSpans = queuedMaxSpans;
  }

  public void setQueuedMaxBytes(Integer queuedMaxBytes) {
    this.queuedMaxBytes = queuedMaxBytes;
  }
}
