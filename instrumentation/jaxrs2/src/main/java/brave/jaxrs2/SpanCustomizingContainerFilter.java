/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.jaxrs2;

import brave.SpanCustomizer;
import javax.inject.Inject;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.RuntimeType.SERVER;

/**
 * Adds application-tier data to an existing http span via {@link ContainerParser}.
 *
 * <p>Use this when you are tracing at a lower layer with {@code brave.servlet.TracingFilter}.
 */
// Currently not using PreMatching because we are attempting to detect if the method is async or not
@Provider
@ConstrainedTo(SERVER)
public final class SpanCustomizingContainerFilter implements ContainerRequestFilter {

  public static SpanCustomizingContainerFilter create() {
    return create(new ContainerParser());
  }

  public static SpanCustomizingContainerFilter create(ContainerParser parser) {
    return new SpanCustomizingContainerFilter(parser);
  }

  final ContainerParser parser;

  @Inject SpanCustomizingContainerFilter(ContainerParser parser) {
    this.parser = parser;
  }

  /** {@link PreMatching} cannot be used: pre-matching doesn't inject the resource info! */
  @Context ResourceInfo resourceInfo;

  @Override public void filter(ContainerRequestContext request) {
    SpanCustomizer span = (SpanCustomizer) request.getProperty(SpanCustomizer.class.getName());
    if (span != null && resourceInfo != null) {
      parser.resourceInfo(resourceInfo, span);
    }
  }
}
