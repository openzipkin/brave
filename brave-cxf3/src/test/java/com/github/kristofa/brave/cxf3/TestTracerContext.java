package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import org.junit.Before;
import org.junit.Test;

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
    TracerContext tracerContext = new TracerContext(brave, null);

    Runnable wrap = tracerContext.wrap(() -> {});

    // this will invoke wrap logic
    wrap.run();

    verify(binder, times(1)).setCurrentSpan(null);
  }

  @Test
  public void testExistingServerSpanRunnable() throws Exception {
    ServerSpan serverSpan = mock(ServerSpan.class);
    TracerContext tracerContext = new TracerContext(brave, serverSpan);

    Runnable wrap = tracerContext.wrap(() -> {});

    // this will invoke wrap logic
    wrap.run();

    verify(binder, times(1)).setCurrentSpan(null);
    verify(binder, times(1)).setCurrentSpan(serverSpan);
  }

  @Test
  public void testEmptyServerSpanCallable() throws Exception {
    TracerContext tracerContext = new TracerContext(brave, null);

    Callable<String> wrap = tracerContext.wrap(() -> "test");

    // this will invoke wrap logic
    wrap.call();

    verify(binder, times(1)).setCurrentSpan(null);
  }

  @Test
  public void testExistingServerSpanCallable() throws Exception {
    ServerSpan serverSpan = mock(ServerSpan.class);
    TracerContext tracerContext = new TracerContext(brave, serverSpan);

    Callable<String> wrap = tracerContext.wrap(() -> "test");

    // this will invoke wrap logic
    wrap.call();

    verify(binder, times(1)).setCurrentSpan(null);
    verify(binder, times(1)).setCurrentSpan(serverSpan);
  }
}