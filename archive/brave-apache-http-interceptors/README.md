# brave-apache-http-interceptors #

HTTP request and response interceptors that can be used with [Apache HttpClient](http://hc.apache.org/httpcomponents-client-4.4.x/index.html)
and [Apache HttpAsyncClient](http://hc.apache.org/httpcomponents-asyncclient-4.1.x/index.html).

Apache HttpClient is probably the most known and used Java Http client. These interceptors make it easy
to integrate with brave to catch/trace client requests. The request interceptor will start a new
Span and submit cs (client sent) annotation. The response interceptor will submit cr (client received)
annotation.

Example of configuring interceptors with http client:

```java
CloseableHttpClient httpclient = HttpClients.custom()
    .addInterceptorFirst(BraveHttpRequestInterceptor.create(brave))
    .addInterceptorFirst(BraveHttpResponseInterceptor.create(brave))
    .build();
```

It is tested with httpclient version 4.4.1.
