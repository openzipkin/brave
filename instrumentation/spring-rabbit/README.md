# brave-instrumentation-spring-rabbit

## Tracing Message Producer
This module contains a tracing interceptor for [Spring RabbitTemplate](https://spring.io/guides/gs/consuming-rest/).
`TracingMessagePostProcessor` adds trace headers to outgoing
rabbit messages. It then reports to Zipkin how long each request takes.

## Configuration
### Producer
Ensure `Tracing` is in the Spring context, then create `TracingMessagePostProcessor` bean. 
Wire this bean as a BeforePublishPostProcessor on the RabbitTemplate that you wish to trace. 
