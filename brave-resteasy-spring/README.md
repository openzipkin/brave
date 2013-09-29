# brave-resteasy-spring #

Latest release available in Maven central:

    <dependency>
        <groupId>com.github.kristofa</groupId>
        <artifactId>brave-resteasy-spring</artifactId>
        <version>2.0.1</version>
    </dependency>


The brave-resteasy-spring module has RESTEasy pre- and postprocess interceptor implementations
that use the ServerTracer to set up the span state.

The preprocess interceptor detects existing trace/span state when a new request comes and sets the
server received annotation if the span needs to be traced.  The postprocess interceptor adds the
server send annotation after which the span will be send to the span collector.

There is a separate example application that shown how to set up and configure the
RESTEAsy integration using Spring -> [https://github.com/kristofa/brave-resteasy-example](https://github.com/kristofa/brave-resteasy-example)

brave-resteasy-spring puts the Spring and RESTEasy dependencies to scope provided which means you are free to choose the
versions yourself and add the dependencies to your own application. 

Important to know is that you should use a recent RESTEasy version otherwise
the integration might not work. It does for example not work with RESTEasy 2.2.1.GA. 
It does work with 2.3.5.Final which is also used in 
[https://github.com/kristofa/brave-resteasy-example](https://github.com/kristofa/brave-resteasy-example). 

When it comes to Spring the oldest version I tried out and which worked was 3.0.5. 
