# brave-spring-beans
This module contains Spring Factory Beans that allow you to configure
tracing with only XML

## Configuration
Bean Factories exist for the following types:
* AsyncReporterFactoryBean - for configuring how often spans are sent to Zipkin
* EndpointFactoryBean - for configuring the service name, IP etc representing this host
* TracingFactoryBean - wires most together, like reporter and log integration
* HttpTracingFactoryBean - for http tagging and sampling policy

Here are some example beans using the factories in this module:
```xml
  <bean id="sender" class="zipkin.reporter2.okhttp3.OkHttpSender" factory-method="create"
      destroy-method="close">
    <constructor-arg type="String" value="http://localhost:9411/api/v2/spans"/>
  </bean>

  <bean id="tracing" class="brave.spring.beans.TracingFactoryBean">
    <property name="localServiceName" value="brave-webmvc-example"/>
    <property name="spanReporter">
      <bean class="brave.spring.beans.AsyncReporterFactoryBean">
        <property name="encoder" value="JSON_V2"/>
        <property name="sender" ref="sender"/>
        <!-- wait up to half a second for any in-flight spans on close -->
        <property name="closeTimeout" value="500"/>
      </bean>
    </property>
    <property name="currentTraceContext">
      <bean class="brave.context.slf4j.MDCCurrentTraceContext" factory-method="create"/>
    </property>
  </bean>

  <bean id="httpTracing" class="brave.spring.beans.HttpTracingFactoryBean">
    <property name="tracing" ref="tracing"/>
  </bean>
```
