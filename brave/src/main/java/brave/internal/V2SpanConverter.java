package brave.internal;

import java.util.Map;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.Span.Kind;

/**
 * This converts {@link zipkin.Span} instances to {@link Span} and visa versa.
 *
 * <p/> Copy-pasted from zipkin.internal.V2SpanConverter version 2.2.2 to avoid shade setup
 */
public final class V2SpanConverter {

  /** Converts the input, parsing {@link Span#kind()} into RPC annotations. */
  public static zipkin.Span toSpan(Span in) {
    String traceId = in.traceId();
    zipkin.Span.Builder result = zipkin.Span.builder()
      .traceId(HexCodec.lowerHexToUnsignedLong(traceId))
      .parentId(in.parentId() != null ? HexCodec.lowerHexToUnsignedLong(in.parentId()) : null)
      .id(HexCodec.lowerHexToUnsignedLong(in.id()))
      .debug(in.debug())
      .name(in.name() != null ? in.name() : ""); // avoid a NPE

    if (traceId.length() == 32) {
      result.traceIdHigh(HexCodec.lowerHexToUnsignedLong(traceId, 0));
    }

    long startTs = in.timestamp() == null ? 0L : in.timestamp();
    Long endTs = in.duration() == null ? 0L : in.timestamp() + in.duration();
    if (startTs != 0L) {
      result.timestamp(startTs);
      result.duration(in.duration());
    }

    zipkin.Endpoint local = in.localEndpoint() != null ? toEndpoint(in.localEndpoint()) : null;
    zipkin.Endpoint remote = in.remoteEndpoint() != null ? toEndpoint(in.remoteEndpoint()) : null;
    Kind kind = in.kind();
    Annotation
      cs = null, sr = null, ss = null, cr = null, ms = null, mr = null, ws = null, wr = null;
    String remoteEndpointType = null;

    boolean wroteEndpoint = false;

    for (int i = 0, length = in.annotations().size(); i < length; i++) {
      zipkin2.Annotation input = in.annotations().get(i);
      Annotation a = Annotation.create(input.timestamp(), input.value(), local);
      if (a.value.length() == 2) {
        if (a.value.equals("cs")) {
          kind = Kind.CLIENT;
          cs = a;
          remoteEndpointType = "sa";
        } else if (a.value.equals("sr")) {
          kind = Kind.SERVER;
          sr = a;
          remoteEndpointType = "ca";
        } else if (a.value.equals("ss")) {
          kind = Kind.SERVER;
          ss = a;
        } else if (a.value.equals("cr")) {
          kind = Kind.CLIENT;
          cr = a;
        } else if (a.value.equals("ms")) {
          kind = Kind.PRODUCER;
          ms = a;
        } else if (a.value.equals("mr")) {
          kind = Kind.CONSUMER;
          mr = a;
        } else if (a.value.equals("ws")) {
          ws = a;
        } else if (a.value.equals("wr")) {
          wr = a;
        } else {
          wroteEndpoint = true;
          result.addAnnotation(a);
        }
      } else {
        wroteEndpoint = true;
        result.addAnnotation(a);
      }
    }

    if (kind != null) {
      switch (kind) {
        case CLIENT:
          remoteEndpointType = "sa";
          if (startTs != 0L) cs = Annotation.create(startTs, "cs", local);
          if (endTs != 0L) cr = Annotation.create(endTs, "cr", local);
          break;
        case SERVER:
          remoteEndpointType = "ca";
          if (startTs != 0L) sr = Annotation.create(startTs, "sr", local);
          if (endTs != 0L) ss = Annotation.create(endTs, "ss", local);
          break;
        case PRODUCER:
          remoteEndpointType = "ma";
          if (startTs != 0L) ms = Annotation.create(startTs, "ms", local);
          if (endTs != 0L) ws = Annotation.create(endTs, "ws", local);
          break;
        case CONSUMER:
          remoteEndpointType = "ma";
          if (startTs != 0L && endTs != 0L) {
            wr = Annotation.create(startTs, "wr", local);
            mr = Annotation.create(endTs, "mr", local);
          } else if (startTs != 0L) {
            mr = Annotation.create(startTs, "mr", local);
          }
          break;
        default:
          throw new AssertionError("update kind mapping");
      }
    }

    for (Map.Entry<String, String> tag : in.tags().entrySet()) {
      wroteEndpoint = true;
      result.addBinaryAnnotation(BinaryAnnotation.create(tag.getKey(), tag.getValue(), local));
    }

    if (cs != null
      || sr != null
      || ss != null
      || cr != null
      || ws != null
      || wr != null
      || ms != null
      || mr != null) {
      if (cs != null) result.addAnnotation(cs);
      if (sr != null) result.addAnnotation(sr);
      if (ss != null) result.addAnnotation(ss);
      if (cr != null) result.addAnnotation(cr);
      if (ws != null) result.addAnnotation(ws);
      if (wr != null) result.addAnnotation(wr);
      if (ms != null) result.addAnnotation(ms);
      if (mr != null) result.addAnnotation(mr);
      wroteEndpoint = true;
    } else if (local != null && remote != null) {
      // special-case when we are missing core annotations, but we have both address annotations
      result.addBinaryAnnotation(BinaryAnnotation.address("ca", local));
      wroteEndpoint = true;
      remoteEndpointType = "sa";
    }

    if (remoteEndpointType != null && remote != null) {
      result.addBinaryAnnotation(BinaryAnnotation.address(remoteEndpointType, remote));
    }

    // don't report server-side timestamp on shared or incomplete spans
    if (Boolean.TRUE.equals(in.shared()) && sr != null) {
      result.timestamp(null).duration(null);
    }
    if (local != null && !wroteEndpoint) { // create a dummy annotation
      result.addBinaryAnnotation(BinaryAnnotation.create("lc", "", local));
    }
    return result.build();
  }

  static zipkin.Endpoint toEndpoint(Endpoint input) {
    zipkin.Endpoint.Builder result = zipkin.Endpoint.builder()
      .serviceName(input.serviceName() != null ? input.serviceName() : "")
      .port(input.port() != null ? input.port() : 0);
    if (input.ipv6() != null) {
      result.parseIp(input.ipv6()); // parse first in case there's a mapped IP
    }
    if (input.ipv4() != null) {
      result.parseIp(input.ipv4());
    }
    return result.build();
  }
}
