# brave-instrumentation
This module a redo of all major instrumentation libraries since Brave 3.
Artifacts have the naming convention "brave-instrumentation-XXX": for
example, the directory "servlet" includes the artifact "brave-instrumentation-servlet".

Here's a brief overview of what's packaged here:

* [grpc](grpc/README.md) - Tracing client and server interceptors for [grpc](github.com/grpc/grpc-java)
* [httpasyncclient](httpasyncclient/README.md) - Tracing decorator for [Apache HttpClient](https://hc.apache.org/httpcomponents-asyncclient-dev/) 4.0+
* [httpclient](httpclient/README.md) - Tracing decorator for [Apache HttpClient](http://hc.apache.org/httpcomponents-client-4.4.x/index.html) 4.3+
* [jaxrs2](jaxrs2/README.md) - Tracing filters and a feature to automatically configure them
* [mysql](mysql/README.md) - Tracing MySQL statement interceptor
* [okhttp3](okhttp3/README.md) - Tracing decorators for [OkHttp](https://github.com/square/okhttp) 3.x
* [p6spy](p6spy/README.md) - Tracing event listener for [P6Spy](https://github.com/p6spy/p6spy) (a proxy for calls to your JDBC driver)
* [servlet](servlet/README.md) - Tracing filter for Servlet 2.5+ (including Async)
* [sparkjava](sparkjava/README.md) - Tracing filters and exception handlers for [SparkJava](http://sparkjava.com/)
* [spring-web](spring-web/README.md) - Tracing interceptor for [Spring RestTemplate](https://spring.io/guides/gs/consuming-rest/)
* [spring-webmvc](spring-webmvc/README.md) - Tracing interceptor for [Spring WebMVC](https://docs.spring.io/spring/docs/current/spring-framework-reference/html/mvc.html)

Here are other tools we provide for configuring or testing instrumentation:
* [http](http/README.md) - `HttpTracing` that allows portable configuration of http instrumentation
* [http-tests](http-tests/README.md) - Interop test suit that all http client and server instrumentation must pass
* [spring-beans](../spring-beans/README.md) - This allows you to setup tracing with XML instead of custom code.
* [benchmarks](benchmarks/README.md) - JMH microbenchmarks that measure instrumentation overhead
