# brave-instrumentation-spring-rabbit

## Tracing for Spring AMQP (RabbitMQ)
This module provides instrumentation for spring-amqp based services. Currently, the only broker supported by spring-amqp is RabbitMQ.

## Configuration

### Message Producer
This module contains a tracing interceptor for [Spring RabbitTemplate](https://spring.io/TODO/).
`TracingMessagePostProcessor` adds trace headers to outgoing rabbit messages. 
It then reports to Zipkin how long each request takes.

To configure tracing for rabbit message producers, 
ensure `Tracing` is in the Spring context, then create `TracingMessagePostProcessor` bean. 
Wire this bean as a BeforePublishPostProcessor on the RabbitTemplate that you wish to trace. 


### Message Consumer
Tracing is supported for spring-rabbit `@RabbitListener` based services.
To configure tracing for rabbit listeners, use SimpleRabbitListenerContainerFactory's setAdviceChain 
to specify a TracingRabbitListenerAdvice.


