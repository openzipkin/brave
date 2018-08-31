# brave-instrumentation-spring-rabbit

## Tracing for Spring Rabbit
This module provides instrumentation for spring-rabbit based services. 

## Common Configuration
To use this instrumentation, first define the common tracing configuration, e.g:
```java
@Bean
public Tracing tracing() {
  return Tracing.newBuilder()
      .localServiceName("spring-amqp-producer")
      .build();
}

@Bean
public SpringRabbitTracing springRabbitTracing(Tracing tracing) {
  return SpringRabbitTracing.newBuilder(tracing)
                            .writeB3SingleFormat(true) // for more efficient propagation
                            .remoteServiceName("my-mq-service")
                            .build();
}
```

### Message Producer
This module contains a tracing interceptor for [RabbitTemplate](https://docs.spring.io/spring-amqp/api/org/springframework/amqp/rabbit/core/RabbitTemplate.html).
`TracingMessagePostProcessor` adds trace headers to outgoing rabbit messages. 
It then reports to Zipkin how long each request takes. To use, define a RabbitTemplate in a Spring config class:

```java
@Bean
public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
    SpringRabbitTracing springRabbitTracing) {
  RabbitTemplate rabbitTemplate = springRabbitTracing.newRabbitTemplate(connectionFactory);
  // other customizations as required
  return rabbitTemplate;
}
```

You can also use `SpringRabbitTracing.decorateRabbitTemplate()` to add
tracing to an existing template.

### Message Consumer
Tracing is supported for spring-rabbit `@RabbitListener` based services.
To configure tracing for rabbit listeners, use the following factory to create a 
[SimpleRabbitListenerContainerFactory](https://docs.spring.io/spring-amqp/api/org/springframework/amqp/rabbit/listener/SimpleMessageListenerContainer.html).
As with the RabbitTemplate, you may provide additional customizations required on the this object as required. 
Note that the tracing functionality is provided through the adviceChain, so if other advices are required
for this ListenerContainerFactory, ensure that they are appended to the adviceChain.

```java
@Bean
public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
    ConnectionFactory connectionFactory,
    SpringRabbitTracing springRabbitTracing
) {
  return springRabbitTracing.newSimpleRabbitListenerContainerFactory(connectionFactory);
}
```

You can also use `SpringRabbitTracing.decorateSimpleRabbitListenerContainerFactory()`
to add tracing to an existing factory.

