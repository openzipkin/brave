# brave-resteasy-spring #

The brave-resteasy-spring module has RESTEasy client and server support which makes the
usage of Brave transparent for your application.

## Configuration
Tracing always needs beans of type `Brave` and `SpanNameProvider`
configured. Make sure these are in place before proceeding.

To setup Resteasy 2.x tracing, tell Spring to scan the package
`com.github.kristofa.brave.resteasy`. There's nothing further needed.

## Details
This module contains:

*   `BraveClientExecutionInterceptor` can be configured to be used with the RestEasy
Client Framework and intercepts every client request made. 
*   `BravePreProcessInterceptor` and `BravePostProcessInterceptor` will intercept requests at the
server side.
  
There is a separate example application that shows how to set up and configure the
RESTEAsy integration using Spring -> https://github.com/openzipkin/brave-resteasy-example

`brave-resteasy-spring` puts the Spring and RESTEasy dependencies to scope provided which means you are free to choose the
versions yourself and add the dependencies to your own application. 

Important to know is that you should use a recent RESTEasy version otherwise
the integration might not work. It does for example not work with RESTEasy 2.2.1.GA. 
It does work with 2.3.5.Final which is also used in 
[https://github.com/kristofa/brave-resteasy-example](https://github.com/kristofa/brave-resteasy-example). 

When it comes to Spring the oldest version I tried out and which worked was 3.0.5. 
