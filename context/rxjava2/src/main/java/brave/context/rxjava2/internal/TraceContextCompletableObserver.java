/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brave.context.rxjava2.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.CompletableObserver;
import io.reactivex.disposables.Disposable;

final class TraceContextCompletableObserver implements CompletableObserver, Disposable {
  final CompletableObserver downstream;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;
  Disposable upstream;

  TraceContextCompletableObserver(
      CompletableObserver downstream, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.downstream = downstream;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  @Override public void onSubscribe(Disposable d) {
    if (!Util.validate(upstream, d)) return;
    upstream = d;
    downstream.onSubscribe(this);
  }

  @Override public void onError(Throwable t) {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      downstream.onError(t);
    } finally {
      scope.close();
    }
  }

  @Override public void onComplete() {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      downstream.onComplete();
    } finally {
      scope.close();
    }
  }

  @Override public boolean isDisposed() {
    return upstream.isDisposed();
  }

  @Override public void dispose() {
    upstream.dispose();
  }
}
