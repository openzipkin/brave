package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Allows binding span from local request thread to a async callback thread that process the
 * result.
 *
 * <p> To be used for async local call the result of which is processed in a separate callback
 * thread. After calling {@link LocalTracer#startNewSpan(String, String)}, call {@link
 * #getCurrentLocalSpan()} and save the result to pass to the callback method (e.g., local final
 * variable) In the callback method, call {@link #setCurrentSpan} before calling {@link
 * LocalTracer#finishSpan()}
 */
public final class LocalSpanThreadBinder {

  private final LocalSpanState state;

  /**
   * Creates a new instance.
   *
   * @param state local span state, cannot be <code>null</code>
   */
  public LocalSpanThreadBinder(LocalSpanState state) {
    this.state = checkNotNull(state, "state");
  }

  /**
   * This should be called in the thread in which the local request made after starting new local
   * span. <p> It returns the current local span which you can keep and bind to the callback thread
   *
   * @return Returned Span can be bound to different callback thread.
   * @see #setCurrentSpan(Span)
   */
  public Span getCurrentLocalSpan() {
    return state.getCurrentLocalSpan();
  }

  /**
   * Binds given span to current thread. This should typically be called when code is invoked in
   * async local callback before the {@link LocalTracer#finishSpan()}
   *
   * @param span Span to bind to current execution thread. Cannot be <code>null</code>.
   */
  public void setCurrentSpan(Span span) {
    state.setCurrentLocalSpan(span);
  }
}
