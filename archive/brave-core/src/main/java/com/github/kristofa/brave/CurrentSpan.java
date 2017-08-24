package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;
import javax.annotation.Nullable;

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
