package brave.cassandra.driver;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.cassandra.tracing.TraceState;

public class CustomPayloadCaptor extends org.apache.cassandra.tracing.Tracing {
  static final AtomicReference<Map<String, ByteBuffer>> ref = new AtomicReference<>();

  @Override protected void stopSessionImpl() {
  }

  @Override protected UUID newSession(UUID sessionId, TraceType traceType,
      Map<String, ByteBuffer> customPayload) {
    ref.set(customPayload);
    return sessionId;
  }

  @Override public TraceState begin(String s, InetAddress inetAddress, Map<String, String> map) {
    return null;
  }

  @Override
  protected TraceState newTraceState(InetAddress inetAddress, UUID uuid, TraceType traceType) {
    return null;
  }

  @Override public void trace(ByteBuffer byteBuffer, String s, int i) {
  }
}
