package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.cxf3.HttpMessage.ServerRequest;
import com.github.kristofa.brave.internal.MaybeAddClientAddress;
import zipkin.Constants;

/**
 * Parses the {@link Constants#CLIENT_ADDR client address}, by looking at "X-Forwarded-For". This
 * performs no DNS lookups.
 *
 * <p>This is a hack as {@link ServerResponseInterceptor} doesn't yet support client addresses.
 */
final class MaybeAddClientAddressFromRequest extends MaybeAddClientAddress<ServerRequest> {

  MaybeAddClientAddressFromRequest(Brave brave) { // intentionally hidden
    super(brave);
  }

  @Override protected byte[] parseAddressBytes(ServerRequest input) {
    return ipStringToBytes(input.getHttpHeaderValue("X-Forwarded-For"));
  }

  @Override protected int parsePort(ServerRequest input) {
    return -1;
  }
}
