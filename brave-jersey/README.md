# brave-jersey #

Contributed by [Henrik Nordvik](https://github.com/zerd).

Contains a Jersey `ClientFilter` implementation that intercepts Jersey client api requests,
passes tracing information to request and creates span with `cs` and `cr` annotations.
The client filter uses 

The module also contains a Servlet filter (javax.servlet.filter) to deal with server
side integration: setting up Endpoint, getting existing span/trace state from request,
create and submit span with `sr`', `ss` annotations.

The Servlet filter can also be used without Jersey. 
