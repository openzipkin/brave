# brave-spring-beans
This module contains Spring Factory Beans that allow you to configure
tracing with only XML

## Configuration
Bean Factories exist for the following types:
* EndpointFactoryBean - for configuring the service name, IP etc representing this host
* TracingFactoryBean - wires most together, like reporter and log integration
* RpcTracingFactoryBean - for RPC tagging and sampling policy
* HttpTracingFactoryBean - for HTTP tagging and sampling policy
* MessagingTracingFactoryBean - for messaging tagging and sampling policy
* CurrentTraceContextFactoryBean - to integrate decorators such as correlation.
* BaggagePropagationFactoryBean - for propagating baggage fields in process and over headers
  * SingleBaggageFieldFactoryBean - configures a single baggage field
* CorrelationScopeDecoratorFactoryBean - for scope decorations such as MDC (logging) field correlation
  * SingleCorrelationFieldFactoryBean - configures a single baggage field for correlation
Here are some example beans using the factories in this module:
```xml
  <bean id="sender" class="zipkin2.reporter.beans.OkHttpSenderFactoryBean">
    <property name="endpoint" value="http://localhost:9411/api/v2/spans"/>
  </bean>

  <bean id="tracing" class="brave.spring.beans.TracingFactoryBean">
    <property name="localServiceName" value="brave-webmvc-example"/>
    <property name="spanReporter">
      <bean class="zipkin2.reporter.beans.AsyncReporterFactoryBean">
        <property name="sender" ref="sender"/>
        <!-- wait up to half a second for any in-flight spans on close -->
        <property name="closeTimeout" value="500"/>
      </bean>
    </property>
    <property name="currentTraceContext">
      <bean class="brave.spring.beans.CurrentTraceContextFactoryBean">
        <property name="scopeDecorators">
          <bean class="brave.context.slf4j.MDCScopeDecorator" factory-method="create"/>
        </property>
      </bean>
    </property>
  </bean>

  <bean id="httpTracing" class="brave.spring.beans.HttpTracingFactoryBean">
    <property name="tracing" ref="tracing"/>
  </bean>

  <bean id="rpcTracing" class="brave.spring.beans.RpcTracingFactoryBean">
    <property name="rpcTracing" ref="tracing"/>
  </bean>

  <bean id="messagingTracing" class="brave.spring.beans.MessagingTracingFactoryBean">
    <property name="messagingTracing" ref="tracing"/>
  </bean>
```

Here's an advanced example, which propagates the request-scoped header "x-vcap-request-id" along
with trace headers:

```xml
  <bean id="userId" class="brave.spring.beans.BaggageFieldFactoryBean">
    <property name="name" value="userId"/>
  </bean>

  <bean id="propagationFactory" class="brave.spring.beans.BaggagePropagationFactoryBean">
    <property name="remoteFields">
      <bean class=\"brave.spring.beans.SingleBaggageFieldFactoryBean\">
        <property name=\"field\" ref=\"userId\"/>
      </bean>
    </property>
  </bean>

  <bean id="tracing" class="brave.spring.beans.TracingFactoryBean">
    <property name="propagationFactory" ref="propagationFactory"/>
```

Here's an example of adding only the trace ID as the correlation property "X-B3-TraceId":

```xml
<util:constant id="traceId" static-field="brave.baggage.BaggageFields.TRACE_ID"/>
<bean id="correlationDecorator" class="brave.spring.beans.CorrelationScopeDecoratorFactoryBean">
  <property name="builder">
    <bean class="brave.context.log4j12.MDCScopeDecorator" factory-method="newBuilder"/>
  </property>
  <property name="configs">
    <list>
      <bean class="brave.spring.beans.SingleCorrelationFieldFactoryBean">
        <property name="baggageField" ref="traceId"/>
        <property name="name" value="X-B3-TraceId"/>
      </bean>
    </list>
  </property>
</bean>
```
