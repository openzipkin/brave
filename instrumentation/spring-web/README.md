# brave-instrumentation-spring-web
This module contains a tracing interceptor for [Spring RestTemplate](https://spring.io/guides/gs/consuming-rest/).
`TracingClientHttpRequestInterceptor` adds trace headers to outgoing
requests. It then reports to Zipkin how long each request takes, along
with relevant tags like the http url.

## Configuration

Tracing always needs a bean of type `HttpTracing` configured. Make sure
it is in place before proceeding.

Then, wire `TracingClientHttpRequestInterceptor` and add it with the
`RestTemplate.setInterceptors` method.
