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
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;
import java.util.concurrent.Callable;

final class TraceContextCallableSingle<T> extends Single<T> implements Callable<T> {
  final SingleSource<T> source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextCallableSingle(
    SingleSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.source = source;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  /**
   * Wraps the observer so that its callbacks run in the assembly context. This does not affect any
   * subscription callbacks.
   */
  @Override protected void subscribeActual(SingleObserver<? super T> o) {
    source.subscribe(Wrappers.wrap(o, contextScoper, assembled));
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
