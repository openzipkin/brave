package brave.netty;

import brave.http.ITHttpServer;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.ComparisonFailure;
import org.junit.Test;

public class ITNettyTracing extends ITHttpServer {
  private int port = 7654;
  HttpSnoopyServer httpSnoopServer = null;
  HttpSnoopyServerInitializer initializer = null;

  @Override
  @Test(expected = ComparisonFailure.class)
  public void reportsClientAddress() throws Exception {
    throw new AssumptionViolatedException("client address can get from request but channel");
  }

  @Override
  protected void init() throws Exception {
    stop();

    initializer = new HttpSnoopyServerInitializer(httpTracing);

    httpSnoopServer = new HttpSnoopyServer(port, initializer);
    //httpSnoopServer.run();
    //Thread server = new Thread(httpSnoopServer);
    httpSnoopServer.start();
  }

  @Override
  protected String url(String path) {
    return "http://localhost:" + port + path;
  }

  @After
  public void stop() throws Exception {
    if (httpSnoopServer != null) {
      httpSnoopServer.interrupt();
    }
  }
}
