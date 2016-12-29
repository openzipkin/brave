package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerSpan;
import org.apache.cxf.jaxrs.ext.ContextProvider;
import org.apache.cxf.message.Message;

import javax.ws.rs.ext.Provider;

/**
 * Provides {@link TracerContext} with current {@link ServerSpan} from cxf {@link Message} {@link org.apache.cxf.message.Exchange}
 */
@Provider
public class TracerContextProvider implements ContextProvider<TracerContext> {

  private final Brave brave;

  public TracerContextProvider(final Brave brave) {
    this.brave = brave;
  }

  @Override
  public TracerContext createContext(Message message) {
    return new TracerContext(brave, message);
  }
}