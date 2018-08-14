# brave-spring-beans
This module contains Spring Factory Beans that allow you to configure
tracing with only XML

## Configuration
Bean Factories exist for the following types:
* EndpointFactoryBean - for configuring the service name, IP etc representing this host
* TracingFactoryBean - wires most together, like reporter and log integration
* HttpTracingFactoryBean - for http tagging and sampling policy

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
```

Here's an advanced example, which propagates the request-scoped header "x-vcap-request-id" along
with trace headers:

```xml
  <bean id="propagationFactory" class="brave.propagation.ExtraFieldPropagation" factory-method="newFactory">
    <constructor-arg index="0">
      <util:constant static-field="brave.propagation.B3Propagation.FACTORY"/>
    </constructor-arg>
    <constructor-arg index="1">
      <list>
        <value>x-vcap-request-id</value>
      </list>
    </constructor-arg>
  </bean>

  <bean id="tracing" class="brave.spring.beans.TracingFactoryBean">
    <property name="propagationFactory" ref="propagationFactory"/>
```
