package brave.spring.servlet;

import brave.parser.Parser;
import java.nio.ByteBuffer;
import javax.servlet.http.HttpServletRequest;
import zipkin.Endpoint;

// TODO: migrate to brave dep as opposed to using from brave-core
import static com.github.kristofa.brave.internal.InetAddresses.ipStringToBytes;

final class ClientAddressParser implements Parser<HttpServletRequest, Endpoint> {
  final String serviceName;

  ClientAddressParser(String serviceName) {
    this.serviceName = serviceName;
  }

  @Override public Endpoint parse(HttpServletRequest request) {
    byte[] addressBytes = ipStringToBytes(request.getHeader("X-Forwarded-For"));
    if (addressBytes == null) addressBytes = ipStringToBytes(request.getRemoteAddr());
    if (addressBytes == null) return null;
    Endpoint.Builder builder = Endpoint.builder().serviceName(serviceName);
    if (addressBytes.length == 4) {
      builder.ipv4(ByteBuffer.wrap(addressBytes).getInt());
    } else if (addressBytes.length == 16) {
      builder.ipv6(addressBytes);
    }
    int port = request.getRemotePort();
    if (port != -1) builder.port(port);
    return builder.build();
  }
}
