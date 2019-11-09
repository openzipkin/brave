# brave-instrumentation-http rationale

## `HttpClientRequest`, `HttpClientResponse`, `HttpServerRequest` and `HttpServerResponse` types

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
[secondary sampling](https://github.com/openzipkin-contrib/zipkin-secondary-sampling) decision.
Without a known http request type, this couldn't be accomplished portably.

Finally, we noticed zipkin-go side-step this problem by defining a top-level
type for http requests and responses. This allows the same object to be used
regardless of purpose, whether that is primary or secondary sampling, or tag
parsing.

All of this led to the introduction of `HttpClientRequest`, `HttpClientResponse`,
`HttpServerRequest` and `HttpServerResponse` types in Brave 5.7.

### Backported Adapters
Our new types like `HttpServerRequest` do not need adapters as they expose
fields like `url()` directly. However, since Brave 4, types such as
`HttpServerParser` and `HttpSampler` have been documented and exposed to users
as a function of an adapter and an underlying type (ex `HttpServletRequest`).

Ex. `HttpClientParser.request(adapter, req, span)`
```java
<Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer span);
```

In order to integrate with these, and reduce code duplication, we backported
`HttpClientHandler` and `HttpServerHandler` to use special adapters which wrap
the new default types as an adapter. Call sites, like
`parser.request(adapter, request, delegate)`, receive that adapter and the
result of `unwrap()` as the request or response parameter. This allows existing
parsers, that received `HttpServletRequest` for example, to continue to receive
the same parameters and not break.

A small design decision was made to incur an extra allocation to implement
adapters via wrapping as opposed to subclassing. The rationale is that one
extra allocation of what is often thousands is not a big deal vs the confusion
of adding more surface to the public api of the new types like
`HttpServerRequest`. For example, if we had `HttpServerRequest` implement
`HttpServerAdapter`, when users look at public methods exposed, they would see
all of the public methods defined in the adapter. This would cause confusion,
something worse than adding an object allocation on these paths.
