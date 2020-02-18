/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave;

import brave.propagation.TraceContext;
import brave.sampler.SamplerFunction;

/**
 * Abstract request type used for parsing and sampling. When implemented, it will be the parameter
 * of {@link SamplerFunction} or {@link TraceContext.Extractor}.
 *
 * <h3>No extensions outside Brave</h3>
 * While this is an abstract type, it should not be subclassed outside the Brave repository. In
 * other words, subtypes are sealed within this source tree.
 *
 * @see SamplerFunction
 * @see TraceContext.Extractor
 * @see TraceContext.Injector
 * @see Response
 * @since 5.9
 */
public abstract class Request {
  /** The remote {@link Span.Kind} describing the direction and type of the request. */
  public abstract Span.Kind spanKind();

  /**
   * Returns the underlying request object or {@code null} if there is none. Here are some request
   * objects: {@code org.apache.http.HttpRequest}, {@code org.apache.dubbo.rpc.Invocation}, {@code
   * org.apache.kafka.clients.consumer.ConsumerRecord}.
   *
   * <p>Note: Some implementations are composed of multiple types, such as a request and a socket
   * address of the client. Moreover, an implementation may change the type returned due to
   * refactoring. Unless you control the implementation, cast carefully (ex using {@code
   * instanceof}) instead of presuming a specific type will always be returned.
   *
   * @since 5.9
   */
  public abstract Object unwrap();

  @Override public String toString() {
    Object unwrapped = unwrap();
    // handles case where unwrap() returning this or null: don't NPE or stack overflow!
    if (unwrapped == null || unwrapped == this) return getClass().getSimpleName();
    return getClass().getSimpleName() + "{" + unwrapped + "}";
  }

  protected Request() { // no instances of this type: only subtypes
  }
}
