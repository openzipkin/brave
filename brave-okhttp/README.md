# brave-okhttp #

This implementation makes it easy to integrate with brave to catch/trace [OkHttp](https://github.com/square/okhttp) requests. The `BraveOkHttpRequestResponseInterceptor` OkHttp [`Interceptor`](http://square.github.io/okhttp/3.x/okhttp/index.html?okhttp3/Interceptor.html) implementation will start a new Span and submit a `cs` (client sent) annotation and then submit `cr` (client received) annotation after the request is received.

Example of configuring the interceptor with OkHttp:

```
import com.github.kristofa.brave.okhttp.BraveOkHttpRequestResponseInterceptor;
import okhttp3.OkHttpClient;

...

OkHttpClient client = new OkHttpClient.Builder()
    .addInterceptor(new BraveOkHttpRequestResponseInterceptor(...))
    .build();
```
