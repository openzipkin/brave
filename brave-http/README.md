# brave-http #

**The com.github.kristofa.brave.client package is deprecated.** 
You should only use code in `com.github.kristofa.brave.http`.
 
This module contains adapters to be used with the `brave-core` interceptors
which are tailored for http clients/servers.

   * `HttpClientRequestAdapter` can be used with brave-core `ClientRequestInterceptor`
   * `HttpClientResponseAdapter` can be used with brave-core `ClientResponseInterceptor`
   * `HttpServerRequestAdapter` can be used with brave-core `ServerRequestInterceptor`
   * `HttpServerResponseAdapter` can be used with brave-core `ServerResponseInterceptor`

These adapters take care of dealing with creating new spans and submitting required annotations and will also
submit some default annotations like `http.uri` for all requests and `http.responsecode` in case of non success response code.
   
To use these adapters you will have to implement `HttpRequest`, `HttpResponse` for client integrations
and `HttpServerRequest` and `HttpResponse` for server integrations. These HttpRequest/HttpResponse/HttpServerRequest
interfaces are adapters that let you integrate with your library of choice.

The `brave-resteasy-spring` module is already adapted to use these new http adapters and can be used as an example.

The Client/Server Request adapters are also configurable. You can for example choose how a span name is represented.
There is an implementation called `DefaultSpanNameProvider` which takes the http method as span name.