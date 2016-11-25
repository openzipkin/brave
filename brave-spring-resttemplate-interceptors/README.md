# brave-spring-resttemplate-interceptors #

Interceptor to be used with the Spring `RestTemplate` class to record the client requests.

## Configuration

Tracing always needs beans of type `Brave` and `SpanNameProvider`
configured. Make sure these are in place before proceeding.

Then, wire `BraveClientHttpRequestInterceptor` and add with the
`RestTemplate.setInterceptors` method.
