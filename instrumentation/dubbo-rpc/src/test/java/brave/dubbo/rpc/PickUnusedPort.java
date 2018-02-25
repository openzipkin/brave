package brave.dubbo.rpc;

import java.io.IOException;
import java.net.ServerSocket;

class PickUnusedPort {
  static int get() {
    try {
      ServerSocket serverSocket = new ServerSocket(0);
      int port = serverSocket.getLocalPort();
      serverSocket.close();
      return port;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
