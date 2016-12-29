package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.Callable;

import static org.mockito.Mockito.*;

public class TestTracerContext {

  private ServerSpanThreadBinder binder;
  private Brave brave;

  @Before
  public void setUp() throws Exception {

    binder = mock(ServerSpanThreadBinder.class);
    brave = mock(Brave.class);
    when(brave.serverSpanThreadBinder()).thenReturn(binder);
  }

  @Test
  public void testEmptyServerSpanRunnable() throws Exception {
    TracerContext tracerContext = new TracerContext(brave, mockMessage(null));

    Runnable wrap = tracerContext.wrap(() -> {});

    // this will invoke wrap logic
    wrap.run();

    verify(binder, times(2)).setCurrentSpan(null);
  }

  @Test
  public void testExistingServerSpanRunnable() throws Exception {
    ServerSpan serverSpan = mock(ServerSpan.class);
    TracerContext tracerContext = new TracerContext(brave, mockMessage(serverSpan));

    Runnable wrap = tracerContext.wrap(() -> {});

    // this will invoke wrap logic
    wrap.run();

    verify(binder, times(1)).setCurrentSpan(null);
    verify(binder, times(1)).setCurrentSpan(serverSpan);
  }

  @Test
  public void testEmptyServerSpanCallable() throws Exception {
    TracerContext tracerContext = new TracerContext(brave, mockMessage(null));

    Callable<String> wrap = tracerContext.wrap(() -> "test");

    // this will invoke wrap logic
    wrap.call();

    verify(binder, times(2)).setCurrentSpan(null);
  }

  @Test
  public void testExistingServerSpanCallable() throws Exception {
    ServerSpan serverSpan = mock(ServerSpan.class);
    TracerContext tracerContext = new TracerContext(brave, mockMessage(serverSpan));

    Callable<String> wrap = tracerContext.wrap(() -> "test");

    // this will invoke wrap logic
    wrap.call();

    verify(binder, times(1)).setCurrentSpan(null);
    verify(binder, times(1)).setCurrentSpan(serverSpan);
  }

  private Message mockMessage(ServerSpan span) {
    Message message = Mockito.mock(Message.class);
    Exchange exchange = Mockito.mock(Exchange.class);

    Mockito.when(exchange.get(BraveCxfConstants.BRAVE_SERVER_SPAN)).thenReturn(span);
    Mockito.when(message.getExchange()).thenReturn(exchange);

    return message;
  }
}