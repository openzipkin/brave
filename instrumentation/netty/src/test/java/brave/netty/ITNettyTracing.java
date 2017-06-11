package brave.netty;

import brave.http.ITHttpServer;

public class ITNettyTracing extends ITHttpServer {
  private int port = 7654;
  private Thread serverThread;
  @Override
  protected void init() throws Exception {

    new Thread(() -> {
      try {
        //HttpSnoopServer.main(args);
        HttpSnoopServer.start(port,null);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }).start();

/*    HttpTracing newTracing = HttpTracing.newBuilder(httpTracing.tracing()).serverName("netty").build();
    HttpSnoopServerInitializer initializer = new HttpSnoopServerInitializer(newTracing);
    HttpSnoopServer httpSnoopServer = new HttpSnoopServer(port, initializer);
    serverThread = new Thread(() -> httpSnoopServer.run());
    serverThread.start();
    Thread.sleep(2000);*/
  }

  @Override
  protected String url(String path) {
    return "http://localhost:" + port + path;
  }

  /*@After
  public void stop() throws Exception {
    Thread.sleep(2000);
    if (serverThread != null) {
      serverThread.interrupt();
    }
  }*/
}
