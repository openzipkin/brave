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

import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.ProtocolViolationException;
import io.reactivex.functions.Function;
import io.reactivex.plugins.RxJavaPlugins;
import org.reactivestreams.Subscription;

/** Junk drawer to avoid dependencies on internal RxJava types. */
public final class Util {
  static final Function<Object, Object> IDENTITY = new Function<Object, Object>() {
    @Override public Object apply(Object v) {
      return v;
    }

    @Override public String toString() {
      return "IdentityFunction";
    }
  };

  // io.reactivex.internal.functions.Functions.identity()
  public static <T> Function<T, T> identity() {
    return (Function<T, T>) IDENTITY;
  }

  // io.reactivex.internal.disposables.DisposableHelper.validate(Disposable, Disposable)
  public static boolean validate(Disposable current, Disposable next) {
    if (next == null) {
      RxJavaPlugins.onError(new NullPointerException("next is null"));
      return false;
    }
    if (current != null) {
      next.dispose();
      RxJavaPlugins.onError(new ProtocolViolationException("Disposable already set!"));
      return false;
    }
    return true;
  }

  // io.reactivex.internal.subscriptions.SubscriptionHelper.validate(Subscription, Subscription)
  public static boolean validate(Subscription current, Subscription next) {
    if (next == null) {
      RxJavaPlugins.onError(new NullPointerException("next is null"));
      return false;
    }
    if (current != null) {
      next.cancel();
      RxJavaPlugins.onError(new ProtocolViolationException("Subscription already set!"));
      return false;
    }
    return true;
  }
}
