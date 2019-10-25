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
* CurrentTraceContextFactoryBean - for scope decorations such as MDC (logging) field correlation
* ExtraFieldPropagationFactoryBean - for propagating extra fields over headers, like "customer-id"

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
  <bean id="propagationFactory" class="brave.spring.beans.ExtraFieldPropagationFactoryBean">
    <property name="fields">
      <list>
        <value>x-vcap-request-id</value>
      </list>
    </property>
  </bean>

  <bean id="tracing" class="brave.spring.beans.TracingFactoryBean">
    <property name="propagationFactory" ref="propagationFactory"/>
```
