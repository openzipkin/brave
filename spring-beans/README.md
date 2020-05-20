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
  <!-- Configuration for how to send spans to Zipkin -->
  <bean id="sender" class="zipkin2.reporter.beans.OkHttpSenderFactoryBean">
    <property name="endpoint" value="http://localhost:9411/api/v2/spans"/>
  </bean>

  <!-- Configuration for how to buffer spans into messages for Zipkin -->
  <bean id="zipkinSpanHandler" class="zipkin2.reporter.beans.AsyncZipkinSpanHandlerFactoryBean">
    <property name="sender" ref="sender"/>
  </bean>

  <!-- Allows log patterns to use %{traceId} and %{spanId} -->
  <bean id="correlationScopeDecorator" class="brave.spring.beans.CorrelationScopeDecoratorFactoryBean">
    <property name="builder">
       <bean class="brave.context.slf4j.MDCScopeDecorator" factory-method="get"/>
    </property>
  </bean>

  <!-- Controls aspects of tracing such as the service name that shows up in the UI -->
  <bean id="tracing" class="brave.spring.beans.TracingFactoryBean">
    <property name="localServiceName" value="brave-webmvc-example"/>
    <property name="spanHandlers" ref="zipkinSpanHandler"/>
    <property name="currentTraceContext">
      <bean class="brave.spring.beans.CurrentTraceContextFactoryBean">
        <property name="scopeDecorators" ref="correlationScopeDecorator"/>
      </bean>
    </property>
  </bean>

  <!-- Allows someone to add tags to a span if a trace is in progress, via SpanCustomizer -->
  <bean id="spanCustomizer" class="brave.CurrentSpanCustomizer" factory-method="create">
    <constructor-arg index="0" ref="tracing"/>
  </bean>

  <!-- Decides how to name and tag spans. By default they are named the same as the http method. -->
  <bean id="httpTracing" class="brave.spring.beans.HttpTracingFactoryBean">
    <property name="tracing" ref="tracing"/>
  </bean>

  <!-- Controls RPC sampling -->
  <bean id="rpcTracing" class="brave.spring.beans.RpcTracingFactoryBean">
    <property name="tracing" ref="tracing"/>
  </bean>

  <!-- Controls Messaging sampling -->
  <bean id="messagingTracing" class="brave.spring.beans.MessagingTracingFactoryBean">
    <property name="messagingTracing" ref="tracing"/>
  </bean>
```

Here's an advanced example, which propagates the request-scoped header "user-name" along
with trace headers, while also making it available in log formats as `%{userName}`.

```xml
  <!-- Defines a propagated field "userName" that uses the remote header "user-name" -->
  <bean id="userNameBaggageField" class="brave.baggage.BaggageField" factory-method="newBuilder">
    <constructor-arg value="userName" />
  </bean>
  <bean id="propagationFactory" class="brave.spring.beans.BaggagePropagationFactoryBean">
    <property name="configs">
      <bean class="brave.spring.beans.SingleBaggageFieldFactoryBean">
        <property name="field" ref="userNameBaggageField" />
        <property name="keyNames" value="user-name" />
      </bean>
    </property>
  </bean>

  <!-- Allows log patterns to use %{traceId} %{spanId} and %{userName} -->
  <bean id="correlationScopeDecorator" class="brave.spring.beans.CorrelationScopeDecoratorFactoryBean">
    <property name="builder">
      <bean class="brave.context.log4j12.MDCScopeDecorator" factory-method="newBuilder"/>
    </property>
    <property name="configs">
      <list>
        <bean class="brave.spring.beans.SingleCorrelationFieldFactoryBean">
          <property name="baggageField">
            <util:constant static-field="brave.baggage.BaggageFields.TRACE_ID"/>
          </property>
        </bean>
        <bean class="brave.spring.beans.SingleCorrelationFieldFactoryBean">
          <property name="baggageField">
            <util:constant static-field="brave.baggage.BaggageFields.SPAN_ID"/>
          </property>
        </bean>
        <bean class="brave.spring.beans.SingleCorrelationFieldFactoryBean">
          <property name="baggageField" ref="userNameBaggageField"/>
        </bean>
      </list>
    </property>
  </bean>
--snip--
```

Here's an example of adding only the trace ID as the correlation property "X-B3-TraceId":

```xml
<bean id="correlationScopeDecorator" class="brave.spring.beans.CorrelationScopeDecoratorFactoryBean">
  <property name="builder">
    <bean class="brave.context.log4j12.MDCScopeDecorator" factory-method="newBuilder"/>
  </property>
  <property name="configs">
    <bean class="brave.spring.beans.SingleCorrelationFieldFactoryBean">
      <property name="baggageField">
        <util:constant static-field="brave.baggage.BaggageFields.TRACE_ID"/>
      </property>
      <property name="name" value="X-B3-TraceId"/>
    </bean>
  </property>
</bean>
```
