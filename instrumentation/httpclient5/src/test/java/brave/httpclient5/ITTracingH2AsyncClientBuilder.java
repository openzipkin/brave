package brave.httpclient5;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Protocol;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.junit.Before;

public class ITTracingH2AsyncClientBuilder extends ITTracingHttpAsyncClientBuilder {
  @Before
  public void setup() throws IOException {
    List<Protocol> protocols = new ArrayList<>();
    protocols.add(Protocol.H2_PRIOR_KNOWLEDGE);
    server.setProtocols(protocols);
    super.setup();
  }

  @Override
  protected CloseableHttpAsyncClient newClient(int port) {
    CloseableHttpAsyncClient result =
      HttpClient5Tracing.create(httpTracing,
        H2AsyncClientBuilder.create().disableAutomaticRetries());
    result.start();
    return result;
  }
}
