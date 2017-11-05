# brave-instrumentation-httpclient
This module contains a tracing decorator for [Apache HttpClient](http://hc.apache.org/httpcomponents-client-4.4.x/index.html) 4.4+.
`TracingHttpClientBuilder` adds trace headers to outgoing requests. It
then reports to Zipkin how long each request takes, along with relevant
tags like the http url.

To enable tracing, create your client using `TracingHttpClientBuilder`.

```java
httpclient = TracingHttpClientBuilder.create(tracing).build();
```

You can also use `TracingCachingHttpClientBuilder` if you depend on
`org.apache.httpcomponents:httpclient-cache`