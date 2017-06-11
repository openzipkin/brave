package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.internal.MaybeAddClientAddress;
import javax.ws.rs.container.ContainerRequestContext;
import zipkin.Constants;

/**
 * Parses the {@link Constants#CLIENT_ADDR client address}, by looking at "X-Forwarded-For". This
 * performs no DNS lookups.
 *
 * <p>This is a hack as {@link ServerResponseInterceptor} doesn't yet support client addresses.
 */
final class MaybeAddClientAddressFromRequest
    extends MaybeAddClientAddress<ContainerRequestContext> {

  MaybeAddClientAddressFromRequest(Brave brave) { // intentionally hidden
    super(brave);
  }

  @Override protected byte[] parseAddressBytes(ContainerRequestContext input) {
    return ipStringToBytes(input.getHeaderString("X-Forwarded-For"));
  }

  @Override protected int parsePort(ContainerRequestContext input) {
    return -1;
  }
}
