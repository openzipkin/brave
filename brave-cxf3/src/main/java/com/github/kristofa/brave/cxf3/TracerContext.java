package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import org.apache.cxf.message.Message;

import java.util.concurrent.Callable;

/**
 * Used to handle {@link ServerSpan} retrieved from cxf message exchange.
 */
public class TracerContext {

  private final ServerSpanThreadBinder serverSpanThreadBinder;
  private final Message message;

  TracerContext(Brave brave, Message message) {
    this.serverSpanThreadBinder = brave.serverSpanThreadBinder();
    this.message = message;
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

    // get server span bind to current thread
    final ServerSpan current = getCurrentServerSpan();
    final ServerSpan serverSpan = getFromExchange();

    return () -> {
      try {
        // When current span is not null, it means it was bind manually by developer
        // or this wrapper is used in sync scenario.
        // Bind server span from Exchange only when current span is null.
        if (current == null) {
          serverSpanThreadBinder.setCurrentSpan(serverSpan);
        }
        command.run();
      } finally {
        serverSpanThreadBinder.setCurrentSpan(current);
      }
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

    // get server span bind to current thread
    final ServerSpan current = getCurrentServerSpan();
    final ServerSpan serverSpan = getFromExchange();

    return () -> {
      try {
        // When current span is not null, it means it was bind manually by developer
        // or this wrapper is used in sync scenario.
        // Bind server span from Exchange only when current span is null.
        if (current == null) {
          serverSpanThreadBinder.setCurrentSpan(serverSpan);
        }
        return command.call();
      } finally {
        serverSpanThreadBinder.setCurrentSpan(current);
      }
    };
  }

  private ServerSpan getCurrentServerSpan() {
    // gets existing server span or reinitializes to ServerSpan#EMPTY using ThreadLocal#initialValue
    final ServerSpan current = serverSpanThreadBinder.getCurrentServerSpan();

    // when value was just initialized
    if (ServerSpan.EMPTY == current) {
      serverSpanThreadBinder.setCurrentSpan(null);
      return null;
    }
    return current;
  }

  private <T> T getFromExchange() {
      return (T) message.getExchange().get(BraveCxfConstants.BRAVE_SERVER_SPAN);
  }
}