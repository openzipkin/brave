package com.github.kristofa.brave.servlet.internal;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.internal.MaybeAddClientAddress;
import javax.servlet.http.HttpServletRequest;
import zipkin.Constants;

/**
 * Parses the {@link Constants#CLIENT_ADDR client address}, by looking at "X-Forwarded-For", then
 * the remote address of the request. This performs no DNS lookups.
 *
 * <p>This is a hack as {@link ServerResponseInterceptor} doesn't yet support client addresses.
 */
public final class MaybeAddClientAddressFromRequest
    extends MaybeAddClientAddress<HttpServletRequest> {

  public static MaybeAddClientAddressFromRequest create(Brave brave) {
    return new MaybeAddClientAddressFromRequest(brave);
  }

  MaybeAddClientAddressFromRequest(Brave brave) { // intentionally hidden
    super(brave);
  }

  @Override protected byte[] parseAddressBytes(HttpServletRequest input) {
    byte[] addressBytes = ipStringToBytes(input.getHeader("X-Forwarded-For"));
    if (addressBytes == null) addressBytes = ipStringToBytes(input.getRemoteAddr());
    return addressBytes;
  }

  @Override protected int parsePort(HttpServletRequest input) {
    return input.getRemotePort();
  }
}
