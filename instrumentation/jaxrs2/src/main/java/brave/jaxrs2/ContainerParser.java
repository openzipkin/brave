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
import brave.http.HttpTracing;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;

/**
 * JAX-RS specific type used to customize traced requests based on the JAX-RS resource.
 *
 * <p>Note: This should not duplicate data added by {@link HttpTracing}. For example, this should
 * not add the tag "http.url".
 */
public class ContainerParser {
  /** Adds no data to the request */
  public static final ContainerParser NOOP = new ContainerParser() {
    @Override protected void resourceInfo(ResourceInfo resourceInfo, SpanCustomizer customizer) {
    }
  };

  /** Simple class name that processed the request. ex BookResource */
  public static final String RESOURCE_CLASS = "jaxrs.resource.class";
  /** Method name that processed the request. ex listOfBooks */
  public static final String RESOURCE_METHOD = "jaxrs.resource.method";

  /**
   * Invoked prior to request invocation during {@link ContainerRequestFilter#filter(ContainerRequestContext)}
   * where the resource info was injected from context.
   *
   * <p>Adds the tags {@link #RESOURCE_CLASS} and {@link #RESOURCE_METHOD}. Override or use {@link
   * #NOOP}
   * to change this behavior.
   */
  protected void resourceInfo(ResourceInfo resourceInfo, SpanCustomizer customizer) {
    customizer.tag(RESOURCE_CLASS, resourceInfo.getResourceClass().getSimpleName());
    customizer.tag(RESOURCE_METHOD, resourceInfo.getResourceMethod().getName());
  }

  public ContainerParser() { // intentionally public for @Inject to work without explicit binding
  }
}
