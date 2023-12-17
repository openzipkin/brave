/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
import io.reactivex.Flowable;
import io.reactivex.FlowableSubscriber;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.MaybeSource;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.parallel.ParallelFlowable;
import java.util.concurrent.Callable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

public class Wrappers {

  public static <T> Subscriber<T> wrap(
    Subscriber<T> downstream, CurrentTraceContext contextScoper, TraceContext assembled) {
    if (downstream instanceof FlowableSubscriber) {
      return new TraceContextFlowableSubscriber<T>((FlowableSubscriber<T>) downstream,
        contextScoper, assembled);
    }
    return new TraceContextSubscriber<T>(downstream, contextScoper, assembled);
  }

  public static Completable wrap(
    CompletableSource source, CurrentTraceContext contextScoper, TraceContext assembled) {
    if (source instanceof Callable) {
      return new TraceContextCallableCompletable(source, contextScoper, assembled);
    }
    return new TraceContextCompletable(source, contextScoper, assembled);
  }

  public static <T> Maybe<T> wrap(
    MaybeSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    if (source instanceof Callable) {
      return new TraceContextCallableMaybe<T>(source, contextScoper, assembled);
    }
    return new TraceContextMaybe<T>(source, contextScoper, assembled);
  }

  public static <T> Single<T> wrap(
    SingleSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    if (source instanceof Callable) {
      return new TraceContextCallableSingle<T>(source, contextScoper, assembled);
    }
    return new TraceContextSingle<T>(source, contextScoper, assembled);
  }

  public static <T> Observable<T> wrap(
    ObservableSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    if (source instanceof Callable) {
      return new TraceContextCallableObservable<T>(source, contextScoper, assembled);
    }
    return new TraceContextObservable<T>(source, contextScoper, assembled);
  }

  public static <T> ConnectableObservable<T> wrap(
    ConnectableObservable<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    return new TraceContextConnectableObservable<T>(source, contextScoper, assembled);
  }

  public static <T> Flowable<T> wrap(
    Publisher<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    if (source instanceof Callable) {
      return new TraceContextCallableFlowable<T>(source, contextScoper, assembled);
    }
    return new TraceContextFlowable<T>(source, contextScoper, assembled);
  }

  public static <T> ConnectableFlowable<T> wrap(
    ConnectableFlowable<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    return new TraceContextConnectableFlowable<T>(source, contextScoper, assembled);
  }

  public static <T> ParallelFlowable<T> wrap(
    ParallelFlowable<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    return new TraceContextParallelFlowable<T>(source, contextScoper, assembled);
  }

  public static <T> Observer<T> wrap(Observer<T> downstream,
    CurrentTraceContext contextScoper, TraceContext assembled) {
    return new TraceContextObserver<T>(downstream, contextScoper, assembled);
  }

  public static <T> SingleObserver<T> wrap(SingleObserver<T> downstream,
    CurrentTraceContext contextScoper, TraceContext assembled) {
    return new TraceContextSingleObserver<T>(downstream, contextScoper, assembled);
  }

  public static <T> MaybeObserver<T> wrap(MaybeObserver<T> downstream,
    CurrentTraceContext contextScoper, TraceContext assembled) {
    return new TraceContextMaybeObserver<T>(downstream, contextScoper, assembled);
  }

  public static CompletableObserver wrap(CompletableObserver downstream,
    CurrentTraceContext contextScoper, TraceContext assembled) {
    return new TraceContextCompletableObserver(downstream, contextScoper, assembled);
  }

  Wrappers() {

  }
}
