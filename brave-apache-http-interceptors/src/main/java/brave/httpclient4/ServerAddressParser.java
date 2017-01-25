package brave.httpclient4;

import brave.parser.Parser;
import java.net.Socket;
import java.nio.ByteBuffer;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ManagedHttpClientConnection;
import zipkin.Endpoint;

final class ServerAddressParser implements Parser<HttpClientContext, Endpoint> {
  final String serviceName;

  ServerAddressParser(String serviceName) {
    this.serviceName = serviceName;
  }

  @Override public Endpoint parse(HttpClientContext source) {
    ManagedHttpClientConnection conn = source.getConnection(ManagedHttpClientConnection.class);
    if (conn == null) return null;
    Socket socket = conn.getSocket();
    if (socket == null) return null;
    Endpoint.Builder builder = Endpoint.builder().serviceName(serviceName);
    byte[] addressBytes = socket.getInetAddress().getAddress();
    if (addressBytes.length == 4) {
      builder.ipv4(ByteBuffer.wrap(addressBytes).getInt());
    } else if (addressBytes.length == 16) {
      builder.ipv6(addressBytes);
    }
    int port = socket.getPort();
    if (port != -1) builder.port(port);
    return builder.build();
  }
}
