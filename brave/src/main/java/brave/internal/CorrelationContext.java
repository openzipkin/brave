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
package brave.internal;

import brave.propagation.CorrelationScopeDecorator;

/**
 * Dispatches methods to synchronize fields with a context such as SLF4J MDC.
 *
 * <p><em>This is internal:</em> All subtypes of {@link CorrelationScopeDecorator} are sealed
 * to this repository until we better understand implications of making this a public type.
 */
// NOTE: revert to abstract class with protected signatures if this is ever promoted to the
// brave.propagation package.
public interface CorrelationContext {
  /**
   * Returns the correlation property of the specified name iff it is a string, or null otherwise.
   */
  @Nullable String get(String name);

  /** Replaces the correlation property of the specified name with the specified value. */
  void put(String name, @Nullable String value);

  /** Removes the correlation property of the specified name. */
  void remove(String name);

  // The below will be sorted into RATIONALE.md once stable
  //
  // NOTES: This design is based on cherry-picking methods available in underlying log contexts, but
  // avoiding operations that accept Map as this implies overhead to construct and iterate over.
  //
  // Here is an example source from Log4J 2:
  //
  // public interface ThreadContextMap2 extends ThreadContextMap {
  //   void putAll(Map<String, String> var1);
  //
  //   StringMap getReadOnlyContextData();
  // }
  //
  // public interface ThreadContextMap {
  //   void clear();
  //
  //   boolean containsKey(String var1);
  //
  //   String get(String var1);
  //
  //   Map<String, String> getCopy();
  //
  //   Map<String, String> getImmutableMapOrNull();
  //
  //   boolean isEmpty();
  //
  //   void put(String var1, String var2);
  //
  //   void remove(String var1);
  // }
  //
  // ## On guarding with previous value
  //
  // While the current design is optimized for log contexts (or those similar such as JFR), you can
  // reasonably think of this like generic contexts such as gRPC and Armeria
  // https://github.com/line/armeria/blob/master/core/src/main/java/com/linecorp/armeria/common/RequestContextStorage.java#L88
  // https://github.com/grpc/grpc-java/blob/master/context/src/main/java/io/grpc/ThreadLocalContextStorage.java
  //
  // They have some design facets to help with overlapping scopes, notably comparing the current
  // value vs the one written prior to reverting a value. Since this feature was designed for
  // contexts which don't have these operators, achieving this would require reading back from the
  // logging context manually. This has two problems, one is performance impact and the other is
  // that the value may have been updated out-of-band. Unlike gRPC context, logging contexts are
  // plain string keys, and are easy to clobber by users or other code. It is therefore hard to tell
  // if inconsistency is due to things under your control or not (ex bad instrumentation vs 3rd
  // party code).
}
