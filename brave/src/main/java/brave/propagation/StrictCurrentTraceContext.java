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
package brave.propagation;

/**
 * Useful when developing instrumentation as state is enforced more strictly.
 *
 * <p>For example, it is instance scoped as opposed to static scoped, not inheritable and throws an
 * exception if a scope is closed on a different thread that it was opened on.
 *
 * @deprecated use {@linkplain StrictScopeDecorator}. This will be removed in Brave v6.
 */
@Deprecated
public final class StrictCurrentTraceContext extends ThreadLocalCurrentTraceContext {
  static final CurrentTraceContext.Builder SCOPE_DECORATING_BUILDER =
    ThreadLocalCurrentTraceContext.newBuilder().addScopeDecorator(new StrictScopeDecorator());

  public StrictCurrentTraceContext() { // Preserve historical public ctor
    // intentionally not inheritable to ensure instrumentation propagation doesn't accidentally work
    // intentionally not static to make explicit when instrumentation need per thread semantics
    super(SCOPE_DECORATING_BUILDER, new ThreadLocal<>());
  }
}
