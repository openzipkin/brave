# brave-client #

Abstraction around `ClientTracer` which avoids code duplication and adds consistency
in libraries that integrate with brave (Apache Httpclient, Jersey, RestEasy,...).

When extending new libraries with brave support it is advised to use this module.
You'll have to implement you own `ClientRequestAdapter` and `ClientResponseAdapter`
and use them with `ClientRequestInterceptor` and `ClientResponseInterceptor`.

You can see several examples of this in `brave-apache-http-interceptors`, 
`brave-jersey` and `brave-resteasy-spring`.

