Installs in/out interceptors for Apache CXF client/server request/respone.

Spring ZipkinFeature allows to attach Zipking tracing support by
adding dependency on brave-cxf and declaring configurable Spring feature that
installs interceptors.

Installing ZipkinFeature might require selecting relevant Spring contexts, e.g. separately
for server and client part.

    <bean id="zipkinFeature" class="com.github.kristofa.brave.cxf.ZipkinFeature">
        <property name="useLogging" value="false"/>
        <property name="useZipkin" value="true"/>
        <property name="isRoot" value="false"/>
        <property name="collectorHost" value="localhost"/>
        <property name="collectorPort" value="9410"/>
        <property name="serviceName" value="pingsrv2"/>
    </bean>

useLogging - test tracing by examine stderr without running ZipkinCollector

useZipkin - use real Zipkin collector interface

isRoot - mode in which we assume that parent tracing context does not exist. For
example if we work from command line utility or performing a web-request with
library that does not have Zipkin instrumentation yet. This mode:
    - automatically creates a span, so all client calls from current thread will be
      binded to the same tree
    - allow to use StartRoot(requestName) and DoneRoot() api to correctly pass name and
      timing for the current function
    - will turn server part of CXF interception off

collectorHost, collectorPort - Thrift TCP interface to Zipkin Collector. May also be
Scribe transport point. Default port is 9410, host to be specified.

serviceName - allows to override current service name. Otherwise it will be
inferred from current request url or will have "default" value.

fedor@yandex-team.ru
