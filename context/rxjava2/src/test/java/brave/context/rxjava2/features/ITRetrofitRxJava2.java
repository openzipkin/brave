package brave.context.rxjava2.features;

import brave.context.rxjava2.CurrentTraceContextAssemblyTracking;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.http.ITHttp;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subscribers.TestSubscriber;
import java.util.function.BiConsumer;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import retrofit2.CallAdapter;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/** This tests that propagation isn't lost when passing through an untraced system. */
public class ITRetrofitRxJava2 extends ITHttp {
  TraceContext context1 = TraceContext.newBuilder().traceId(1L).spanId(1L).build();
  CurrentTraceContextAssemblyTracking contextTracking =
      CurrentTraceContextAssemblyTracking.create(currentTraceContext);

  @Rule public MockWebServer server = new MockWebServer();
  CurrentTraceContextObserver currentTraceContextObserver = new CurrentTraceContextObserver();

  @Before
  public void setup() {
    RxJavaPlugins.reset();
    contextTracking.enable();
  }

  @After
  public void tearDown() {
    contextTracking.disable();
  }

  interface Service {
    @GET("/")
    Completable completable();

    @GET("/")
    Maybe<ResponseBody> maybe();

    @GET("/")
    Observable<ResponseBody> observable();

    @GET("/")
    Single<ResponseBody> single();

    @GET("/")
    Flowable<ResponseBody> flowable();
  }

  @Test
  public void createAsync_completable_success() {
    rxjava_createAsync_success(
        (service, observer) -> {
          try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context1)) {
            service.completable().subscribe(observer);
          }
        });
  }

  @Test
  public void createAsync_maybe_success() {
    rxjava_createAsync_success(
        (service, observer) -> {
          try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context1)) {
            service.maybe().subscribe(observer);
          }
        });
  }

  @Test
  public void createAsync_observable_success() {
    rxjava_createAsync_success(
        (service, observer) -> {
          try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context1)) {
            service.observable().subscribe(observer);
          }
        });
  }

  @Test
  public void createAsync_single_success() {
    rxjava_createAsync_success(
        (service, observer) -> {
          try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context1)) {
            service.single().subscribe(observer);
          }
        });
  }

  private void rxjava_createAsync_success(BiConsumer<Service, TestObserver<Object>> subscriber) {
    TestObserver<Object> observer = new TestObserver<>(currentTraceContextObserver);

    Service service = service(RxJava2CallAdapterFactory.createAsync());
    subscriber.accept(service, observer);

    // enqueue later
    server.enqueue(new MockResponse());

    observer.awaitTerminalEvent(1, SECONDS);
    observer.assertComplete();
    assertThat(currentTraceContextObserver.onComplete).isEqualTo(context1);
  }

  @Test
  public void createAsync_flowable_success() {
    rx_createAsync_success(
        (service, subscriber) -> {
          try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context1)) {
            service.flowable().subscribe(subscriber);
          }
        });
  }

  private void rx_createAsync_success(BiConsumer<Service, TestSubscriber<Object>> subscriber) {
    TestSubscriber<Object> observer = new TestSubscriber<>(currentTraceContextObserver);

    Service service = service(RxJava2CallAdapterFactory.createAsync());
    subscriber.accept(service, observer);

    // enqueue later
    server.enqueue(new MockResponse());

    observer.awaitTerminalEvent(1, SECONDS);
    observer.assertComplete();
    assertThat(currentTraceContextObserver.onComplete).isEqualTo(context1);
  }

  Service service(CallAdapter.Factory callAdapterFactory) {
    return new Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addCallAdapterFactory(callAdapterFactory)
        .build()
        .create(Service.class);
  }

  class CurrentTraceContextObserver implements Observer<Object>, Subscriber<Object> {
    volatile TraceContext onSubscribe, onNext, onError, onComplete;

    @Override
    public void onSubscribe(Disposable d) {
      onSubscribe = currentTraceContext.get();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
      onSubscribe = currentTraceContext.get();
    }

    @Override
    public void onNext(Object object) {
      onNext = currentTraceContext.get();
    }

    @Override
    public void onError(Throwable e) {
      onError = currentTraceContext.get();
    }

    @Override
    public void onComplete() {
      onComplete = currentTraceContext.get();
    }
  }
}
