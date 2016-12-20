package com.github.kristofa.brave.internal;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import zipkin.Constants;

import static com.github.kristofa.brave.internal.Util.checkNotNull;
import static zipkin.Constants.CLIENT_ADDR;

/**
 * Parses the {@link Constants#CLIENT_ADDR client address}, possibly by looking at
 * "X-Forwarded-For", then the remote address of the input. This performs no DNS lookups.
 *
 * <p>This is a hack as {@code com.github.kristofa.brave.http.HttpServerRequest} is an interface and
 * would break api if we changed it. Moreover, this can work on non-http input types.
 */
public abstract class MaybeAddClientAddress<T> {
  final ServerSpanThreadBinder threadBinder;

  protected MaybeAddClientAddress(Brave brave) { // accepts brave so we can re-factor thread state
    this.threadBinder = checkNotNull(brave, "brave").serverSpanThreadBinder();
  }

  public final void accept(T input) {
    // Kick out if we can't read the current span
    ServerSpan serverSpan = threadBinder.getCurrentServerSpan();
    Span span = serverSpan != null ? serverSpan.getSpan() : null;
    if (span == null) return;

    // Kick out if we can't cheaply read the address
    byte[] addressBytes;
    try {
      addressBytes = parseAddressBytes(input);
      if (addressBytes == null) return;
    } catch (RuntimeException e) {
      return;
    }

    // Build an endpoint with no service name (rather than risk cluttering the service list!)
    Endpoint.Builder builder = Endpoint.builder().serviceName("");
    if (addressBytes.length == 4) {
      builder.ipv4(ByteBuffer.wrap(addressBytes).getInt());
    } else if (addressBytes.length == 16) {
      // https://tools.ietf.org/html/rfc4291#section-2.5.5.2
      boolean maybeIpv4Compat = true; // if it starts with 80 unset bits
      for (int i = 0; i < 10; i++) {
        if (addressBytes[i] != 0) {
          maybeIpv4Compat = false;
          break;
        }
      }
      if (maybeIpv4Compat) {
        ByteBuffer buffer = ByteBuffer.wrap(addressBytes, 10, 6);
        short flag = buffer.getShort();
        if (flag == 0 || flag == -1) { // IPv4-Compatible or IPv4-Mapped
          builder.ipv4(buffer.getInt());
        } else {
          builder.ipv6(addressBytes);
        }
      } else {
        builder.ipv6(addressBytes);
      }
    } else {
      return; // invalid
    }
    try {
      int port = parsePort(input);
      if (port > 0) builder.port(port);
    } catch (RuntimeException ignore) {
      // still store the ip address
    }
    Endpoint ca = builder.build();

    // Internally, ServerTracer locks on span when adding an address. let's do that, too
    synchronized (span) {
      span.addToBinary_annotations(BinaryAnnotation.address(CLIENT_ADDR, ca));
    }
  }

  /**
   * Returns the 4 byte ipv4 address or the 16-byte ipv6 address associated with the input type.
   *
   * <pre>{@code
   * byte[] addressBytes = ipStringToBytes(input.getHeader("X-Forwarded-For"));
   * if (addressBytes == null) addressBytes = ipStringToBytes(input.getRemoteAddr());
   * return addressBytes;
   * }</pre>
   */
  protected abstract byte[] parseAddressBytes(T input);

  /** Returns port associated with the input or <=0 if unreadable. */
  protected abstract int parsePort(T input);

  /**
   * Returns the {@link InetAddress#getAddress()} having the given string representation or null if
   * unable to parse.
   *
   * <p>This deliberately avoids all nameservice lookups (e.g. no DNS).
   *
   * @param ipString {@code String} containing an IPv4 or IPv6 string literal, e.g. {@code
   * "192.168.0.1"} or {@code "2001:db8::1"}
   */
  @Nullable
  protected byte[] ipStringToBytes(String ipString) {
    return InetAddresses.ipStringToBytes(ipString);
  }
}
