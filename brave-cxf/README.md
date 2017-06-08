Installs in/out interceptors for Apache CXF client/server request/respone.

Spring ZipkinFeature allows to attach Zipking tracing support by
adding dependency on brave-cxf and declaring configurable Spring feature that
installs interceptors.

Installing ZipkinFeature might require selecting relevant Spring contexts, e.g. separately
for server and client part.

    <bean id="zipkinFeature" class="com.github.kristofa.brave.cxf.ZipkinFeature">
        <property name="useLogging" value="false"/>
        <property name="useZipkin" value="true"/>
        <property name="collectorHost" value="localhost"/>
        <property name="collectorPort" value="9410"/>
         <!-- property name="clientServiceName" value="pingsrv2"/> -->
    </bean>

useLogging - test tracing by examine stderr without running ZipkinCollector

useZipkin - use real Zipkin collector interface

clientServiceName - allows to override parent calling service name for
clients that are not web services, e.g. command line tools.

fedor57@gmail.com
