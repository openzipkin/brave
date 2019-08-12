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
import io.reactivex.Flowable;
import io.reactivex.FlowableSubscriber;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * We implement {@linkplain FlowableSubscriber}, not {@linkplain Subscriber} as the only call site
 * is {@code Flowable#subscribeActual(Subscriber)} which is guaranteed to only take a {@linkplain
 * FlowableSubscriber}.
 */
class TraceContextFlowableSubscriber<T> extends TraceContextSubscriber<T>
  implements FlowableSubscriber<T>, Subscription {

  TraceContextFlowableSubscriber(
    FlowableSubscriber<T> downstream, CurrentTraceContext contextScoper,
    TraceContext assembled) {
    super(downstream, contextScoper, assembled);
  }

  @Override public void request(long n) {
    upstream.request(n);
  }

  @Override public void cancel() {
    upstream.cancel();
  }
}
