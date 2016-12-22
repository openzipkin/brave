package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;

import java.util.concurrent.Callable;

/**
 * Used to handle {@link ServerSpan} retrieved from cxf message exchange.
 */
public class TracerContext {

  private final ServerSpanThreadBinder serverSpanThreadBinder;
  private final ServerSpan serverSpan;

  TracerContext(Brave brave, ServerSpan serverSpan) {
    this.serverSpanThreadBinder = brave.serverSpanThreadBinder();
    this.serverSpan = serverSpan;
  }

  /**
   * Wraps provided {@link Runnable and ensures same {@link ServerSpan} context as the thread from which the Runnable was executed.
   * {@link ServerSpan} is taken from cxf {@link org.apache.cxf.message.Message} {@link org.apache.cxf.message.Exchange}
   *
   * @param command {@link Runnable}
   * @return wrapped {@link Runnable}
   */
  public Runnable wrap(Runnable command) {
    if (command == null) {
      return null;
    }

    // we should clean this thread, ServerSpan will finish on another one
    serverSpanThreadBinder.setCurrentSpan(null);

    return () -> {
      if (serverSpan != null) {
        serverSpanThreadBinder.setCurrentSpan(serverSpan);
      }
      command.run();
    };
  }

  /**
   * Wraps provided {@link Callable} and ensures same {@link ServerSpan} context as the thread from which the Callable was executed.
   * {@link ServerSpan} is taken from cxf {@link org.apache.cxf.message.Message} {@link org.apache.cxf.message.Exchange}
   *
   * @param command {@link Callable}
   * @return wrapped {@link Callable}
   */
  public <V> Callable<V> wrap(Callable<V> command) {
    if (command == null) {
      return null;
    }

    // we should clean this thread, ServerSpan will finish on another one
    serverSpanThreadBinder.setCurrentSpan(null);

    return () -> {
      if (serverSpan != null) {
        serverSpanThreadBinder.setCurrentSpan(serverSpan);
      }
      return command.call();
    };
  }
}