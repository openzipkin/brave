package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

public final class BraveJaxWsServerInInterceptor extends AbstractBraveServerInInterceptor {

  /**
   * Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize.
   */
  public static BraveJaxWsServerInInterceptor create(Brave brave) {
    return new Builder(brave).build();
  }

  public static Builder builder(Brave brave) {
    return new Builder(brave);
  }

  public static final class Builder {
    final Brave brave;
    SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();

    Builder(Brave brave) { // intentionally hidden
      this.brave = checkNotNull(brave, "brave");
    }

    public Builder spanNameProvider(SpanNameProvider spanNameProvider) {
      this.spanNameProvider = checkNotNull(spanNameProvider, "spanNameProvider");
      return this;
    }

    public BraveJaxWsServerInInterceptor build() {
      return new BraveJaxWsServerInInterceptor(this);
    }
  }

  BraveJaxWsServerInInterceptor(Builder b) { // intentionally hidden
    super(b.brave, b.spanNameProvider);
  }
}