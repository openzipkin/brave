package brave.netty;

import brave.http.ITHttpServer;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.ComparisonFailure;
import org.junit.Test;

public class ITNettyTracing extends ITHttpServer {
  private int port = 4567;
  HttpSnoopyServer httpSnoopServer = null;

  @Override
  @Test(expected = ComparisonFailure.class)
  public void reportsClientAddress() throws Exception {
    throw new AssumptionViolatedException("client address can get from channel not request");
  }

  @Override
  protected void init() throws Exception {
    stop();
    
    HttpSnoopyServerInitializer initializer = new HttpSnoopyServerInitializer(httpTracing);
    httpSnoopServer = new HttpSnoopyServer(port, initializer);
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
