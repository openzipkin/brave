package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.twitter.zipkin.gen.Span;

abstract class CurrentSpan { // intentionally not public

  /**
   * Returns the implicit span associated with the current context, or null if there is none.
   *
   * <p>This is used by {@linkplain LocalTracer}, {@linkplain LocalTracer}
   *
   * @return Span to which to add annotations. Can be <code>null</code>. In that case the different
   * submit methods will not do anything.
   */
  @Nullable abstract Span get(); // intentionally package protected
}
