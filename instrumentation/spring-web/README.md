# brave-instrumentation-spring-web
This module contains tracing interceptors for [Spring RestTemplate](https://spring.io/guides/gs/consuming-rest/).
`TracingClientHttpRequestInterceptor` and `TracingAsyncClientHttpRequestInterceptor` add trace
headers to outgoing requests. They then report to Zipkin how long each request took, along with
relevant tags like the http url.

## Configuration

Tracing always needs a bean of type `HttpTracing` configured. Make sure
it is in place before proceeding. Here's an example in [XML](https://github.com/openzipkin/brave-webmvc-example/blob/master/webmvc25/src/main/webapp/WEB-INF/spring-webmvc-servlet.xml) and [Java](https://github.com/openzipkin/brave-webmvc-example/blob/master/webmvc4/src/main/java/brave/webmvc/TracingConfiguration.java).

Then, wire `TracingClientHttpRequestInterceptor` and add it with the
`RestTemplate.setInterceptors` method. If you are using `AsyncRestTemplate` and Spring 4.3+, you can
wire `AsyncTracingClientHttpRequestInterceptor` and add it via `AsyncRestTemplate.setInterceptors`.
