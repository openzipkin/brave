package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerSpanThreadBinder;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import java.io.IOException;
import java.lang.annotation.Annotation;

/**
 * Cleans current server thread binder in async scenarios.
 */
class BraveAsyncJaxRsThreadCleaner implements ContainerRequestFilter {

  @Context
  private ResourceInfo resourceInfo;
  private final ServerSpanThreadBinder serverThreadBinder;

  BraveAsyncJaxRsThreadCleaner(Brave brave) {
    this.serverThreadBinder = brave.serverSpanThreadBinder();
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) throws IOException {
    if (isAsyncResponse()) {
      serverThreadBinder.setCurrentSpan(null);
    }
  }

  protected boolean isAsyncResponse() {
    for (final Annotation[] annotations : resourceInfo.getResourceMethod().getParameterAnnotations()) {
      for (final Annotation annotation : annotations) {
        if (annotation.annotationType().equals(Suspended.class)) {
          return true;
        }
      }
    }
    return false;
  }
}