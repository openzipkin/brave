# brave-instrumentation-httpasyncclient
This module contains a tracing decorator for [Apache HttpClient](https://hc.apache.org/httpcomponents-asyncclient-dev/) 4.0+.
`TracingAsyncHttpClientBuilder` adds trace headers to outgoing requests. It
then reports to Zipkin how long each request takes, along with relevant
tags like the http url.

To enable tracing, create your client using `TracingHttpAsyncClientBuilder`.

```java
httpasyncclient = TracingAsyncHttpClientBuilder.create(tracing).build();
```
