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
package brave.context.rxjava2.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.parallel.ParallelFlowable;
import org.reactivestreams.Subscriber;

final class TraceContextParallelFlowable<T> extends ParallelFlowable<T> {
  final ParallelFlowable<T> source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextParallelFlowable(
    ParallelFlowable<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.source = source;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  @Override public int parallelism() {
    return source.parallelism();
  }

  /**
   * Wraps the subscribers so that their callbacks run in the assembly context. This does not affect
   * any subscription callbacks.
   */
  @Override public void subscribe(Subscriber<? super T>[] s) {
    if (!validate(s)) return;
    int n = s.length;
    @SuppressWarnings("unchecked")
    Subscriber<? super T>[] parents = new Subscriber[n];
    for (int i = 0; i < n; i++) {
      Subscriber<? super T> z = s[i];
      parents[i] = Wrappers.wrap(z, contextScoper, assembled);
    }
    source.subscribe(parents);
  }
}
