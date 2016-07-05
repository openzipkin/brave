package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import java.util.List;
import java.util.TreeMap;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServerInterceptorsTest {
  ReporterForTesting reporter = new ReporterForTesting();
  Brave brave = new Brave.Builder().reporter(reporter).build();
  Message message = mock(Message.class);

  @Test
  public void test() {

    BraveServerInInterceptor inInterceptor = BraveServerInInterceptor.create(brave);
    BraveServerOutInterceptor outInterceptor = BraveServerOutInterceptor.create(brave);

    ExchangeImpl exchange = new ExchangeImpl();

    when(message.get(Message.REQUEST_URL)).thenReturn("http://localhost:8000");
    when(message.get(Message.HTTP_REQUEST_METHOD)).thenReturn("post");
    when(message.get(Message.RESPONSE_CODE)).thenReturn(200);
    when(message.get(Message.PROTOCOL_HEADERS)).thenReturn(new TreeMap<String, List<String>>());
    when(message.getExchange()).thenReturn(exchange);

    // server receives
    inInterceptor.handleMessage(message);
    // server responds
    outInterceptor.handleMessage(message);

    assertThat(reporter.getCollectedSpans()).hasSize(1);
  }
}