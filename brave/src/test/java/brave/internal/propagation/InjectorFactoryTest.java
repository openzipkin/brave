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
package brave.internal.propagation;

import brave.Request;
import brave.Span.Kind;
import brave.internal.propagation.InjectorFactory.CompositeInjectorFunction;
import brave.internal.propagation.InjectorFactory.DeferredInjector;
import brave.internal.propagation.InjectorFactory.InjectorFunction;
import brave.internal.propagation.InjectorFactory.RemoteInjector;
import brave.propagation.Propagation.RemoteSetter;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static brave.internal.propagation.InjectorFactory.injectorFunction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InjectorFactoryTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build();
  EnumSet<Kind> injectableKinds = EnumSet.of(Kind.CLIENT, Kind.PRODUCER, Kind.CONSUMER);

  Object notRequest = new Object();
  Setter<Object, String> setter = mock(Setter.class);
  Request request = mock(Request.class);
  RemoteSetter<Request> remoteSetter = mock(RemoteSetter.class);

  // mocks are too complex when verifying injectors called in a loop
  AtomicInteger oneCount = new AtomicInteger();
  InjectorFunction one = newInjectorFunction("one", oneCount);
  AtomicInteger twoCount = new AtomicInteger();
  InjectorFunction two = newInjectorFunction("two", twoCount);
  AtomicInteger threeCount = new AtomicInteger();
  InjectorFunction three = newInjectorFunction("three", threeCount);
  AtomicInteger fourCount = new AtomicInteger();
  InjectorFunction four = newInjectorFunction("four", fourCount);

  List<AtomicInteger> allCounts = Arrays.asList(oneCount, twoCount, threeCount, fourCount);

  InjectorFactory oneFunction = InjectorFactory.newBuilder(one).build();
  InjectorFactory twoFunctions = InjectorFactory.newBuilder(one)
      .injectorFunctions(one, two)
      .clientInjectorFunctions(one, two)
      .producerInjectorFunctions(one, two)
      .consumerInjectorFunctions(one, two)
      .build();
  InjectorFactory kindBasedFunctions = InjectorFactory.newBuilder(one)
      .injectorFunctions(one)
      .clientInjectorFunctions(two)
      .producerInjectorFunctions(three)
      .consumerInjectorFunctions(four)
      .build();

  @Test public void injectorFunction_emptyIgnored() {
    InjectorFunction existing = mock(InjectorFunction.class);
    assertThat(injectorFunction(existing))
        .isSameAs(existing);
  }

  @Test public void injectorFunction_noopIgnored() {
    InjectorFunction existing = mock(InjectorFunction.class);
    assertThat(injectorFunction(existing, InjectorFunction.NOOP))
        .isSameAs(existing);
  }

  @Test public void injectorFunction_null() {
    InjectorFunction existing = mock(InjectorFunction.class);
    assertThatThrownBy(() -> injectorFunction(existing, null))
        .hasMessage("injectorFunctions == null");
    assertThatThrownBy(() -> injectorFunction(existing, new InjectorFunction[] {null}))
        .hasMessage("injectorFunction == null");
    assertThatThrownBy(() -> injectorFunction(existing, one, null))
        .hasMessage("injectorFunction == null");
  }

  @Test public void injectorFunction_single() {
    InjectorFunction existing = mock(InjectorFunction.class);
    assertThat(injectorFunction(existing, two))
        .isSameAs(two);
  }

  @Test public void injectorFunction_composite() {
    InjectorFunction existing = mock(InjectorFunction.class);
    CompositeInjectorFunction injectorFunction =
        (CompositeInjectorFunction) injectorFunction(existing, two, three);

    assertThat(injectorFunction.injectorFunctions)
        .containsExactly(two, three);

    injectorFunction.inject(setter, context, notRequest);
    assertThat(twoCount.getAndSet(0)).isOne();
    assertThat(threeCount.getAndSet(0)).isOne();
  }

  @Test public void oneFunction_keyNames() {
    assertThat(oneFunction.keyNames()).containsExactly("one");
  }

  @Test public void oneFunction_injects_deferred() {
    DeferredInjector<Object> deferredInjector =
        (DeferredInjector<Object>) oneFunction.newInjector(setter);

    for (Kind kind : injectableKinds) {
      when(request.spanKind()).thenReturn(kind);

      deferredInjector.inject(context, request);
      assertThat(oneCount.getAndSet(0)).isOne();
    }

    deferredInjector.inject(context, notRequest);
    assertThat(oneCount.getAndSet(0)).isOne();

    // works with nonsense
    when(request.spanKind()).thenReturn(Kind.SERVER);
    deferredInjector.inject(context, request);
    assertThat(oneCount.getAndSet(0)).isOne();
  }

  @Test public void oneFunction_injects_remote() {
    when(remoteSetter.spanKind()).thenReturn(Kind.CLIENT);
    RemoteInjector<Request> remoteInjector =
        (RemoteInjector<Request>) oneFunction.newInjector(remoteSetter);

    for (Kind kind : injectableKinds) {
      when(remoteSetter.spanKind()).thenReturn(kind);

      Injector<Request> nextRemoteInjector = oneFunction.newInjector(remoteSetter);
      assertThat(nextRemoteInjector).isEqualTo(remoteInjector);
      assertThat(nextRemoteInjector).hasSameHashCodeAs(remoteInjector);
      assertThat(nextRemoteInjector).hasToString(remoteInjector.toString());

      assertThat(nextRemoteInjector).extracting("injectorFunction")
          .isEqualTo(remoteInjector.injectorFunction);

      nextRemoteInjector.inject(context, request);
      assertThat(oneCount.getAndSet(0)).isOne();
    }
  }

  @Test public void twoFunctions_keyNames() {
    assertThat(twoFunctions.keyNames()).containsExactly("one", "two");
  }

  @Test public void twoFunctions_injects_deferred() {
    DeferredInjector<Object> deferredInjector =
        (DeferredInjector<Object>) twoFunctions.newInjector(setter);

    for (Kind kind : injectableKinds) {
      when(request.spanKind()).thenReturn(kind);

      deferredInjector.inject(context, request);
      assertThat(oneCount.getAndSet(0)).isOne();
      assertThat(twoCount.getAndSet(0)).isOne();
    }

    deferredInjector.inject(context, notRequest);
    assertThat(oneCount.getAndSet(0)).isOne();
    assertThat(twoCount.getAndSet(0)).isOne();

    // works with nonsense
    when(request.spanKind()).thenReturn(Kind.SERVER);
    deferredInjector.inject(context, request);
    assertThat(oneCount.getAndSet(0)).isOne();
    assertThat(twoCount.getAndSet(0)).isOne();
  }

  @Test public void twoFunctions_injects_remote() {
    when(remoteSetter.spanKind()).thenReturn(Kind.CLIENT);
    RemoteInjector<Request> remoteInjector =
        (RemoteInjector<Request>) twoFunctions.newInjector(remoteSetter);

    for (Kind kind : injectableKinds) {
      when(remoteSetter.spanKind()).thenReturn(kind);

      Injector<Request> nextRemoteInjector = twoFunctions.newInjector(remoteSetter);
      assertThat(nextRemoteInjector).isEqualTo(remoteInjector);
      assertThat(nextRemoteInjector).hasSameHashCodeAs(remoteInjector);
      assertThat(nextRemoteInjector).hasToString(remoteInjector.toString());

      assertThat(nextRemoteInjector).extracting("injectorFunction")
          .isEqualTo(remoteInjector.injectorFunction);

      nextRemoteInjector.inject(context, request);
      assertThat(oneCount.getAndSet(0)).isOne();
      assertThat(twoCount.getAndSet(0)).isOne();
    }
  }

  @Test public void kindBasedFunctions_keyNames() {
    assertThat(kindBasedFunctions.keyNames()).containsExactly("four", "three", "two", "one");
  }

  @Test public void kindBasedFunctions_injects_deferred_client() {
    kindBasedFunctions_injects_deferred(Kind.CLIENT, twoCount);
  }

  @Test public void kindBasedFunctions_injects_deferred_producer() {
    kindBasedFunctions_injects_deferred(Kind.PRODUCER, threeCount);
  }

  @Test public void kindBasedFunctions_injects_deferred_consumer() {
    kindBasedFunctions_injects_deferred(Kind.CONSUMER, fourCount);
  }

  @Test public void kindBasedFunctions_injects_deferred_server() {
    // SERVER is not injectable, this is for someone with buggy understanding of injection
    // we verify the default is called.
    kindBasedFunctions_injects_deferred(Kind.SERVER, oneCount);
  }

  @Test public void kindBasedFunctions_injects_deferred_notRequest() {
    DeferredInjector<Object> nextDeferredInjector =
        (DeferredInjector) kindBasedFunctions.newInjector(setter);
    nextDeferredInjector.inject(context, notRequest);

    for (AtomicInteger callCount : allCounts) {
      if (callCount.equals(oneCount)) {
        assertThat(callCount.getAndSet(0)).isOne();
      } else {
        assertThat(callCount.getAndSet(0)).isZero();
      }
    }
  }

  void kindBasedFunctions_injects_deferred(Kind kind, AtomicInteger kindCallCount) {
    when(request.spanKind()).thenReturn(kind);

    DeferredInjector<Object> nextDeferredInjector =
        (DeferredInjector) kindBasedFunctions.newInjector(setter);
    nextDeferredInjector.inject(context, request);

    for (AtomicInteger callCount : allCounts) {
      if (callCount.equals(kindCallCount)) {
        assertThat(callCount.getAndSet(0)).isOne();
      } else {
        assertThat(callCount.getAndSet(0)).isZero();
      }
    }
  }

  @Test public void kindBasedFunctions_injects_remote_client() {
    kindBasedFunctions_injects_remote(Kind.CLIENT, twoCount);
  }

  @Test public void kindBasedFunctions_injects_remote_producer() {
    kindBasedFunctions_injects_remote(Kind.PRODUCER, threeCount);
  }

  @Test public void kindBasedFunctions_injects_remote_consumer() {
    kindBasedFunctions_injects_remote(Kind.CONSUMER, fourCount);
  }

  @Test public void kindBasedFunctions_injects_remote_server() {
    // SERVER is not injectable, this is for someone with buggy understanding of injection
    // we verify the default is called.
    when(request.spanKind()).thenReturn(Kind.SERVER);
    kindBasedFunctions_injects_remote(Kind.SERVER, oneCount);
  }

  void kindBasedFunctions_injects_remote(Kind kind, AtomicInteger kindCallCount) {
    when(remoteSetter.spanKind()).thenReturn(kind);

    Injector<Request> nextRemoteInjector = kindBasedFunctions.newInjector(remoteSetter);
    nextRemoteInjector.inject(context, request);

    for (AtomicInteger callCount : allCounts) {
      if (callCount.equals(kindCallCount)) {
        assertThat(callCount.getAndSet(0)).isOne();
      } else {
        assertThat(callCount.getAndSet(0)).isZero();
      }
    }
  }

  static InjectorFunction newInjectorFunction(String keyName, AtomicInteger callCount) {
    return new InjectorFunction() {
      @Override public List<String> keyNames() {
        return Arrays.asList(keyName);
      }

      @Override public <R> void inject(Setter<R, String> setter, TraceContext context, R request) {
        callCount.incrementAndGet();
      }
    };
  }
}
