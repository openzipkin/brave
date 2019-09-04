# brave-instrumentation-http rationale

## `HttpServerRequest` and `HttpServerResponse` types

Initially, we had a partial function of (adapter, request) to parse attributes
like the HTTP path from a request. This made sense in the http abstraction, as
all types that needed these attributes, such as parsers and samplers, were in
the same package and library. It also saved an object allocation otherwise
needed to wrap a native type, and the indirection of unwrapping as needed.

This worked, but especially on the response path this broke down. We found that
attributes in the request, such as the method and matched route, are needed at
response time, but aren't themselves a part of the response. Over time, we
accumulated response wrappers or thread locals to pass these attributes to the
response as many response objects lacked a property bag.

Then, we noticed people wanting to do more during extraction than look at
headers. For example, Netflix wanted to inspect the http path to make a
secondary sampling decision. Without a known http request type, this couldn't
be accomplished portably.

Finally, we noticed zipkin-go side-step this problem by defining a top-level
type for http requests and responses. This allows the same object to be used
regardless of purpose, whether that is primary or secondary sampling, or tag
parsing.

All of this led to the introduction of `HttpServerRequest` and
`HttpServerResponse` types in Brave 5.7, along with `HttpServerAdapter.LEGACY`
which allows integration with existing sampler and parser code.
