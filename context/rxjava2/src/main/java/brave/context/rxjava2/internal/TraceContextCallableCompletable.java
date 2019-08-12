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
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableSource;
import java.util.concurrent.Callable;

final class TraceContextCallableCompletable<T> extends Completable implements Callable<T> {
  final CompletableSource source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextCallableCompletable(
    CompletableSource source, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.source = source;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  /**
   * Wraps the observer so that its callbacks run in the assembly context. This does not affect any
   * subscription callbacks.
   */
  @Override protected void subscribeActual(CompletableObserver s) {
    source.subscribe(Wrappers.wrap(s, contextScoper, assembled));
  }

  /**
   * The value in the source is computed synchronously, at subscription time. We don't re-scope this
   * call because it would interfere with the subscription context.
   *
   * <p>See https://github.com/ReactiveX/RxJava/wiki/Writing-operators-for-2.0#callable-and-scalarcallable
   */
  @Override @SuppressWarnings("unchecked") public T call() throws Exception {
    return ((Callable<T>) source).call();
  }
}
