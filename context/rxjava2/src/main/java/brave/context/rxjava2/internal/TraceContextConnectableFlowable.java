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
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.disposables.Disposable;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Consumer;
import org.reactivestreams.Subscriber;

final class TraceContextConnectableFlowable<T> extends ConnectableFlowable<T> {
  final ConnectableFlowable<T> source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextConnectableFlowable(
    ConnectableFlowable<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.source = source;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  /**
   * Wraps the subscriber so that its callbacks run in the assembly context. This does not affect
   * any subscription callbacks.
   */
  @Override protected void subscribeActual(Subscriber<? super T> s) {
    source.subscribe(Wrappers.wrap(s, contextScoper, assembled));
  }

  @Override public void connect(Consumer<? super Disposable> connection) {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      source.connect(connection);
    } finally {
      scope.close();
    }
  }
}
