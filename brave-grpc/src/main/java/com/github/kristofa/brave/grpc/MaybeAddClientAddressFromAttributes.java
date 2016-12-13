package com.github.kristofa.brave.grpc;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.internal.MaybeAddClientAddress;
import io.grpc.Attributes;
import io.grpc.ServerCall;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import zipkin.Constants;

/**
 * Parses the {@link Constants#CLIENT_ADDR client address}, by looking at "X-Forwarded-For", then
 * the remote address of the headers. This performs no DNS lookups.
 */
final class MaybeAddClientAddressFromAttributes extends MaybeAddClientAddress<Attributes> {

  MaybeAddClientAddressFromAttributes(Brave brave) { // intentionally hidden
    super(brave);
  }

  @Override protected byte[] parseAddressBytes(Attributes input) {
    SocketAddress address = input.get(ServerCall.REMOTE_ADDR_KEY);
    if (!(address instanceof InetSocketAddress)) return null;
    return ((InetSocketAddress) address).getAddress().getAddress();
  }

  @Override protected int parsePort(Attributes input) {
    SocketAddress address = input.get(ServerCall.REMOTE_ADDR_KEY);
    if (!(address instanceof InetSocketAddress)) return 0;
    return ((InetSocketAddress) address).getPort();
  }
}
