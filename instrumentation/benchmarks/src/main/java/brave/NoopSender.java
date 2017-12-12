package brave;

import java.util.List;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.codec.Encoding;
import zipkin2.reporter.BytesMessageEncoder;
import zipkin2.reporter.Sender;

final class NoopSender extends Sender {

  final Encoding encoding = Encoding.JSON;
  final BytesMessageEncoder messageEncoder = BytesMessageEncoder.forEncoding(encoding);

  /** close is typically called from a different thread */
  volatile boolean closeCalled;

  @Override public int messageMaxBytes() {
    return 1024;
  }

  @Override public Encoding encoding() {
    return encoding;
  }

  @Override public int messageSizeInBytes(List<byte[]> encodedSpans) {
    return encoding().listSizeInBytes(encodedSpans);
  }

  @Override public int messageSizeInBytes(int encodedSizeInBytes) {
    return encoding().listSizeInBytes(encodedSizeInBytes);
  }

  @Override public Call<Void> sendSpans(List<byte[]> encodedSpans) {
    messageEncoder.encode(encodedSpans);
    return Call.create(null);
  }

  @Override public CheckResult check() {
    return CheckResult.OK;
  }

  @Override public void close() {
    closeCalled = true;
  }

  @Override public String toString() {
    return "NoopSender";
  }
}
