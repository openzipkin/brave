Brave Core Library Rationale
==============

Brave's design lends from past experience and similar open source work.
Quite a lot of decisions were driven by portability with Brave 3, and the
dozens of individuals involved in that. Later decisions focus on what we
learned since the previous major. For example, Brave 5 was the last version of
Brave 4, minus deprecation. This provides continuity, yet allows innovation
through minor revisions.

While many ideas are our own, there are notable aspects borrowed or adapted
from others. It is our goal to cite when we learned something through a prior
library, as that allows people to research any rationale that predates our
usage.

Below is always incomplete, and always improvable. We don't document every
thought as it would betray productivity and make this document unreadable.
Rationale here should be limited to impactful designs, and aspects non-obvious,
non-conventional or subtle.

## Java conventions
Brave 4's public namespace is more defensive than the past, using a package
accessor design from [OkHttp](https://github.com/square/okhttp).

We only expose types public internally or after significant demand. This keeps
the api small and easier to manage when charting migration paths. Otherwise,
types are always package private.

Methods should only be marked public when they are intentional apis or
inheritance requires it. This practice prevents accidental dependence on
utilities.

### Why no private symbols? (methods and fields)
Brave is a library with embedded use cases, such as inside Java agents or
Android code.

For example, Android has a [hard limit on total methods in an application](https://developer.android.com/studio/build/multidex#avoid).
Fields marked private imply accessors in order to share state in the same
package. We routinely share state, such as sampling flag internals inside a
package. If we marked fields private, we'd count against that limit without
adding value.

Modifiers on fields and methods are distracting to read and increase the size
of the bytecode generated during compilation. By recovering the size otherwise
spent on private modifiers, we not only avoid hitting limits, but we are also
able to add more code with the same jar size.

For example, Brave 5.12 remains less than 250KiB, with no dependencies, all
features including deprecation bridges, and an embedded json serializer.

This means we do not support sharing our packages with third parties, but we
do support an "always share inside a package" in our repository. In other
words, we trust our developers to proceed with caution. In the first seven
years of project history, we have had no issues raised with this policy.

### Java 1.6
Brave is a telemetry library and can only trace applications in JREs that
support its bytecode. Java 1.6 is still a traced technology of August 2020.
Here's a quote from Fabian Lange of Instana, a tracing vendor, at that time:

> We have 1.6 / 1.7 customers and we happily support them with our auto instrumentation.

While other modules in this project are higher Java levels, the core library
remains at 1.6. This allows the highest applicability at the cost of
inconvenience to core library maintainers.

### Zero dependency policy
Brave is a telemetry library, which means it is used inside other low-level
libraries. Attempting to do that would limit the applicability of Brave, which
is an anti-goal. Instead, we choose to use nothing except floor Java version
features, currently Java 6.

Here's an example of when things that seem right aren't. We once dropped our
internal `@Nullable` annotation (which is source retention), in favor of JSR
305 (which is runtime retention). In doing so, we got `null` analysis from
Intellij. However, we entered a swamp of dependency conflicts, which started
with OSGi (making us switch to a service mix bundle for the annotations), and
later Java 9 (conflict with apps using jax-ws). In summary, it is easiest for
us to have no dependencies also, as we don't inherit tug-of-war between modular
frameworks who each have a different "right" answer for even annotations!

Incidentally, IntelliJ can be configured to use `brave.internal.Nullable`, now.
Search for `Nullable` under inspections to configure this.

### Why `new NullPointerException("xxx == null")`
For public entry points, we eagerly check null and throw `NullPointerException`
with a message like "xxx == null". This is not a normal pre-condition, such as
argument validation, which you'd throw `IllegalArgumentException` for. What's
happening here is we are making debugging (literally NPEs are bugs) easier, by
not deferring to Java to raise the NPE. If we deferred, it could be confusing
which local was null, especially as deferring results in an exception with no
message.

## Stateful Span object
Brave 4 allows you to pass around a Span object which can report itself
to Zipkin when finished. This is better than using thread contexts in
some cases, particularly where many async hops are in use. The Span api
is derived from OpenTracing, narrowed to more cleanly match Zipkin's
abstraction. As a result, a bridge from Brave 4 to OpenTracing v0.20.2
is relatively little code. It should be able to implement future
versions of OpenTracing as well.

## ScopedSpan type
Brave 4.19 introduced `ScopedSpan` which derives influence primarily from
[OpenCensus SpanBuilder.startScopedSpan() Api](https://github.com/census-instrumentation/opencensus-java/blob/f852100ad9b77522fabd3c0b29e78202aed3a26e/api/src/main/java/io/opencensus/trace/SpanBuilder.java#L229), a convenience type to
start work and ensure scope is visible downstream. One main difference here is
we assume the scoped span is used in the same thread, so mark it as such. Also,
we reduce the size of the api to help secure users against code drift.

## Error Handling
Brave 4.19 added `ErrorParser`, internally used by `Span.error(Throwable)`. This
design incubated in [Spring Cloud Sleuth](https://github.com/spring-cloud/spring-cloud-sleuth) prior to becoming a primary type here.
Our care and docs around error handling in general is credit to [Nike Wingtips](https://github.com/Nike-Inc/wingtips#warning-about-error-handling-when-using-try-with-resources-to-autoclose-spans).

### Why we don't tag "error=false"
"error=false" is almost always the result of mis-mapping from OpenTracing who
defined a boolean error bit, but not a string message.
https://github.com/opentracing/specification/blob/master/semantic_conventions.yaml#L15

Zipkin by extension Brave's "error" tag is about the error code or message.
There are very few times you will not know a message of some kind. The only
known examples are some RPC frameworks who only have a boolean bit in some
error cases. End users will typically not be implementing such low-level
instrumentation, so should not be steered towards such an edge case, especially
when more meaningful and better messages almost always exist, in worst case the
type name of the error.

Regardless, no one could define a value range for the "error" (message) tag.
There are far too many different frameworks with different people involved. It
is better to steer instrumentors towards getting the best short error message.

Zipkin's tag system allows empty values, and its query api allows tag key only
searches. When considered in this context, the value of the "error" tag is less
important than its existence. This also provides an option for those few edge
cases where there's only a single bit known.. just set the value to empty.

Since OpenTracing caused people to think about error in boolean terms, it is
worth mentioning some glitches this creates in isolated or aggregate traces.

When someone thinks in terms of boolean, they are led to think "error=false" is
something that should be added on success, or by default. However, this is full
of problems. For example, errors can happen late and depending on backend,
overwriting a false with true can cause problems when aggregating error counts.
Next, even when someone thinks something might be success there is no value
gained in asserting this. For example, one-way RPC give no response back. If
you tag "error=false", it could not only be wrong, but also not correctable
later. In short, it is more likely to cause confusion by adding "error=false"
vs not adding a tag at all.

It is particularly important to understand users writing instrumentation will
make mistakes. If we suggest an erroneous practice, it will hurt them in
surprising ways. For example, what was described above resulted in support load
as OpenTracing users colored all spans red by mistake. Knowing pitfalls is
important, but not steering users into pitfalls is more important.

To avoid leading people into problems, we have never described the "error" tag
in boolean terms. It is the the code or message a person instead. For example,
if you know you have an error, but don't know what it is, tag "error" -> "".
This conveys "I don't know the error message". If instead, we said tag "true",
it would tempt people to think "not false". This would be a slippery slope back
into the "error=false" debacle. In any case, if anyone is in a position where
they are inclined to tag "error" -> "", they probably already need help. As
mentioned above, this is almost never the case in practice.

Users who have a success code, and want to tag it should just use a different
tag name than "error" to convey that. For example, gRPC has a status which
includes success, as does HTTP. In other words, the "error" tag does not need
to be overloaded to say the opposite. The better practice is to use an
appropriate alternative tag, such as "http.status_code".

### Error overrides
In some cases, the "error" code added may be misrepresentative. We formerly had
users complaining about junk service diagrams because 404 was classified as an
error. `HTTP GET` returning 404 is sometimes treated like `Map.containsKey` in
certain REST dialects. You can imagine how aggregate service error stats become
with this in mind.

For this reason, all abstractions we make should have a default mapping, yet
allow users to change the primary "error" status if they know better.

## Recorder architecture
Much of Brave 4's architecture is borrowed from Finagle, whose design
implies a separation between the propagated trace context and the data
collected in process. For example, Brave's MutableSpanMap is the same
overall design as Finagle's. The internals of MutableSpanMap were adapted
from [WeakConcurrentMap](https://github.com/raphw/weak-lock-free).

## Propagation Api
OpenTracing's Tracer type has methods to inject or extract a trace
context from a carrier. While naming is similar, Brave optimizes for
direct integration with request types (such as HTTP request) vs routing
through an intermediate (such as a map). Brave also considers propagation
a separate api from the tracer.

### Why does `Setter` not allow putting `null`?
`Setter` cannot allow clearing values as it is not guaranteed to work. For
example, you can't clear headers made with `URLConnection`, only replace or add
to them.

For this reason, when wrapping messages that are processed multiple times, such
as in messaging, you must clear keys on your own.

There is a problem when an abstraction hides follow-up requests, such as
HTTP redirects or retries, as multiple requests will result in the same span.
This implies invalid timing and a repeated upstream ID seen by potentially
multiple servers! For this reason, always prefer sending headers (using
`Setter`) at the lowest abstraction possible, and raise issues with frameworks
that hide the ability to see follow-up requests.

### Why does `Setter` not allow multiple values?
First, the most common multi-map header is HTTP, `Setter` also works on
messaging and RPC where multi-map fields are unusual.

HTTP [RFC 7230](https://tools.ietf.org/html/rfc7230#section-3.2.2) allows encoding of multiple fields with the same name, but this
only applies to fields which define themselves as comma-separated. The `Setter`
abstraction has no knowledge of if the request is an HTTP request, and even if
it did, it wouldn't have knowledge of the format rules per field. That's the
responsibility of the `Propagation` implementation. Hence, the `Injector`,
which knows format rules, should do comma joining when valid.

Some think that sending a header in comma-separated format, implies a receiver
is allowed to split it into separate fields. This is not allowed by RFC 7230.
Only formats known to be comma-separated are allowed to be joined as one value.
The visa-versa is not true. For example, our [secondary sampling format](https://github.com/openzipkin-contrib/zipkin-secondary-sampling/blob/master/docs/design.md) is
comma-separated, but it is not defined as being allowed to be encoded as
multiple values. Any intermediate that randomly splits values of unknown
formats, just on account of seeing a comma, is not following the RFC 7230 and
therefore has a bug that should be fixed.

### Why does `Getter` imply joining HTTP headers on comma?
The `Getter` API only can return a single string, so that implies when multiple
headers are received, an HTTP implementation should join them on comma before
returning.

HTTP [RFC 7230](https://tools.ietf.org/html/rfc7230#section-3.2.2) allows encoding of multiple fields with the same name, but this
only applies to fields which define themselves as comma-separated. If a header
is not comma-separated, but was sent multiple times, that was an encoding
mistake.

One may raise the special case of `Set-Cookie` as a reason why we shouldn't
imply joining on comma. This is not a request header, it is a response header.
Hence, it isn't valid to discuss until such time, if ever, we handle responses.
If we did, `Set-Cookie` is not defined as a trace context header, nor is it
defined as baggage. Implementations could choose to special-case or blacklist
this when implementing the `Getter` api, as `Getter` is only used for
propagation fields.

One may also think that we should join any delimited string with comma, but
again HTTP only defines this for comma-separated lists with the Cookie special
case. This means those using other delimiters should never expect to be able to
send in multiple header fields. In practice, as trace propagation amplifies
overhead, anything that starts discussing multiple header encoding is already
questionable to apply.

In practice, notably only [trace-context](https://w3c.github.io/trace-context/)
chose to use comma-separated encoding even after it introduced parsing
problems. They could have avoided this by simply not using a comma.

For example, [X-Amzn-Trace-Id](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-request-tracing.html) uses semi-colon encoding, so implies no
implementation toil for the same reasons mentioned above multiple times: only
comma-separated HTTP header fields are allowed to be sent multiple times.

### Deprecation of Propagation K parameter

The Propagation `<K>` parameter allowed integration with requests that had
headers, but didn't use String types. This was over-generalized, as in practice
only gRPC instrumentation ever used this key type (`Metadata.Key`).

Removing this `<K>` parameter dramatically simplifies the model as it removes
the need to explain the key factory and the edge case it supported, which can
be accomplished differently.

For example, and equivalent functionality works via internally converting and
caching gRPC propagation keys like so:
```java
/** Creates constant keys for use in propagating trace identifiers or baggage. */
static Map<String, Key<String>> nameToKey(Propagation<String> propagation) {
  Map<String, Key<String>> result = new LinkedHashMap<>();
  for (String keyName : propagation.keys()) {
    result.put(keyName, Key.of(keyName, Metadata.ASCII_STRING_MARSHALLER));
  }
  for (String keyName : BaggagePropagation.baggageKeyNames(propagation)) {
    result.put(keyName, Key.of(keyName, Metadata.ASCII_STRING_MARSHALLER));
  }
  return result;
}
```

In summary, even though we have a generic parameter, only `Propagation<String>`
will be used in practice. We keep the generic parameter to avoid an API breaks
on a frequently used type, even if seeing it is a distraction and having it at
all was a mistake in hindsight.

## Current Tracer Api
The first design work of `Tracing.currentTracer()` started in [Brave 3](https://github.com/openzipkin/brave/pull/210),
which was itself influenced by Finagle's implicit Tracer api. This feature
is noted as edge-case, when other means to get a reference to a trace are
impossible. The only instrumentation that needed this was JDBC.

Returning a possibly null reference from `Tracing.currentTracer()` implies:
* Users never cache the reference returned (noted in javadocs)
* Less code and type constraints on Tracer vs a lazy forwarding delegate
* Less documentation as we don't have to explain what a Noop tracer does

## CurrentTraceContext Api
The first design of `CurrentTraceContext` was borrowed from `ContextUtils`
in Google's [instrumentation-java](https://github.com/google/instrumentation-java) project.
This was possible because of high collaboration between the projects and
an almost identical goal. Slight departures including naming (which prefers
Guice's naming conventions), and that the object scoped is a TraceContext
vs a Span.

Propagating a trace context instead of a span is a right fit for several reasons:
* `TraceContext` can be created without a reference to a tracer
* Common tasks like making child spans and staining log context only need the context
* Brave's recorder is keyed on context, so there's no feature loss in this choice

## Local Sampling flag
The [Census](https://opencensus.io/) project has a concept of a [SampledSpanStore](https://github.com/census-instrumentation/opencensus-java/blob/master/api/src/main/java/io/opencensus/trace/export/SampledSpanStore.java).
Typically, you configure a span name pattern or choose [individual spans](https://github.com/census-instrumentation/opencensus-java/blob/660e8f375bb483a3eb817940b3aa8534f86da314/api/src/main/java/io/opencensus/trace/EndSpanOptions.java#L65)
for local (in-process) storage. This storage is used to power
administrative pages named zPages. For example, [Tracez](https://github.com/census-instrumentation/opencensus-java/blob/660e8f375bb483a3eb817940b3aa8534f86da314/contrib/zpages/src/main/java/io/opencensus/contrib/zpages/TracezZPageHandler.java#L220)
displays spans sampled locally which have errors or crossed a latency
threshold.

The "sample local" vocab in Census was re-used in Brave to describe the
act of keeping data that isn't going to necessarily end up in Zipkin.

The main difference in Brave is implementation. For example, the `sampledLocal`
flag in Brave is a part of the TraceContext and so can be triggered when
headers are parsed. This is needed to provide custom sampling mechanisms.
Also, Brave has a small core library and doesn't package any specific
storage abstraction, admin pages or latency processors. As such, we can't
define specifically what sampled local means as Census' could. All it
means is that `SpanHandler` will see data for that trace context.

## SpanHandler Api
Brave had for a long time re-used zipkin's Reporter library, which is
like Census' SpanExporter in so far that they both allow pushing a specific
format elsewhere, usually to a service. Brave's [SpanHandler](https://github.com/openzipkin/brave/blob/master/brave/src/main/java/brave/handler/SpanHandler.java)
is a little more like Census' [SpanExporter.Handler](https://github.com/census-instrumentation/opencensus-java/blob/master/api/src/main/java/io/opencensus/trace/export/SpanExporter.java)
in so far as the structure includes the trace context.. something we need
access to in order to do things like advanced sampling.

Where it differs is that the `MutableSpan` in Brave is.. well.. mutable,
and this allows you to cheaply change data. Also, it isn't a struct based
on a proto, so it can hold references to objects like `Exception`. This
allows us to render data into different formats such as [Amazon's stack frames](https://docs.aws.amazon.com/xray/latest/devguide/xray-api-segmentdocuments.html)
without having to guess what will be needed by parsing up front. As
error parsing is deferred, overhead is less in cases where errors are not
recorded (such as is the case on spans intentionally dropped).

It is notable that the initial design, `FinishedSpanHandler`, only could see
spans at the end. Brave 5.12 deprecated `FinishedSpanHandler` for `SpanHandler`
to allow more sophisticated integrations.

### Why not just an `end` hook?
`SpanHandler` has a `begin` and also `end` hook. The main use cases for `begin`
are correlated data collection. In other words, you can collect implicit data
from the span, or data from alternate apis, correlated with Brave's `context`.
Upon `end`, you can harvest that data along with what's recorded by default
into `MutableSpan` or a custom type.

Let's take an example of Google Stackdriver. In their model, [child_span_count](https://cloud.google.com/trace/docs/reference/v2/rpc/google.devtools.cloudtrace.v2#google.devtools.cloudtrace.v2.Span)
refers to the direct children of a span. Without a `begin` hook, especially
without one that has reference to a parent, you cannot easily aggregate this
information.

### Why no `start` or `finish` hook?
The hooks `begin` and `end` are intentionally different than `Span.start()`
or `Span.finish()`, as they reflect one of possibly many recordings of a given
trace context. Sometimes spans are never started, and other times their start
timestamp overwritten is overwritten. Hence we avoid using state terminology
that overlaps with `Span` methods. `SpanHandler` handles recorded data, but is
not a one-to-one mapping to the state model.

### Why `SpanHandler` instead of `SpanCollector` or `SpanInterceptor`?
We chose the name `SpanHandler` to cause the least confusion possible, even if
the name is imperfect.

`SpanCollector` was the original name in Brave <4, but the name was confusing
as most think of server-side collectors.

`SpanInterceptor` was considered, as it is used for common functionality, such
as in `kafka-clients`. However, interceptor usually means getting in the middle
of something. ex

```
void intercept(thing, chain){
  return chain.proceed(thing);
}
```

At the end, we chose a name we use elsewhere, such as `HttpServerHandler`, as
the semantics are similar enough to what's going on, and the familiarity is
relevant.

## Rate-limiting sampler
`RateLimitingSampler` was made to allow Amazon X-Ray rules to be
expressed in Brave. We considered their [Reservoir design](https://github.com/aws/aws-xray-sdk-java/blob/2.0.1/aws-xray-recorder-sdk-core/src/main/java/com/amazonaws/xray/strategy/sampling/reservoir/Reservoir.java).
Our implementation differs as it removes a race condition and attempts
to be more fair by distributing accept decisions every decisecond.

## Baggage
The name Baggage was first introduced by Brown University in [Pivot Tracing](https://people.mpi-sws.org/~jcmace/papers/mace2015pivot.pdf)
as maps, sets and tuples. They then spun baggage out as a standalone component,
[BaggageContext](https://people.mpi-sws.org/~jcmace/papers/mace2018universal.pdf)
and considered some of the nuances of making it general purpose. The
implementations proposed in these papers are different to the implementation
here, but conceptually the goal is the same: to propagate "arbitrary stuff"
with a request.

### `BaggageField.ValueUpdater`
`BaggageField.ValueUpdater` is a functional interface that allows field
updates, possibly to `null`. It returns a `boolean` to avoid synchronizing
contexts when an update resulted in no change.

Concretely, when an update returns `false`, we don't attempt to flush changes
to a correlation context such as `MDC`.

### Context storage
`BaggageFields` is copy-on-write context storage of `BaggageField` values.
It is added only once to a request extraction or trace context regardless of
the count of fields.

Copy-on-write means a call to `BaggageField.updateValue` will replace its
internal state with a copy of the prior (usually the parent). Until an update
occurs (possibly never!), a constant `initialState` is shared by all trace
contexts. In other words, if baggage is configured, but never used, the only
overhead is one state wrapper (`BaggageFields`) per `TraceContext`. If
only one context has a change in that local root, then only one copy of the
state is made.

Put short the copy-on-write aspect limits overhead under the assumption that
changes to `BaggageField` values are infrequent. We believe this assumption
holds due to practice. For example, common baggage are immutable after parsing
a request, such as request ID, country code, device ID, etc.

#### Internal state is an `Object[]`
`BaggageField` values are `String`, so to hold state means an association of
some kind between `BaggageField` and a single `String` value. Updates to
baggage are generally infrequent, sometimes never, and many times only when
extracting headers from a request. As such, we choose pair-wise layout in an
object array (`Object[]`).

To remind, pair-wise means an association `{k1=v1, k2=v2}` is laid out in an
array as `[k1, v1, k2, v2]`. The size of the association is `array.length/2`.
Retrieving a value means finding the key's index and returning `array[i+1]`

In our specific case, we add `null` values for all known fields. For example,
if `BaggageConfiguration` is initialized with fields countryCode and deviceId,
the initial state array is `[countryCode, null, deviceId, null]`

Retaining fields even when `null` allows among many things, the following:
* Queries for all potential field names (remote propagation)
* Constant time index lookup of predefined fields.

In practice, we use a constant `HashMap` to find the index of a predefined
fields. This is how we achieve constant time index lookup, while also keeping
iteration performance of an array!

### `BaggageField.ValueUpdater`
`BaggageField.ValueUpdater` is a functional interface that allows field
updates, possibly to `null`.

It returns a `boolean` to inform callers an update was ignored, which can
happen when the value didn't change or the field is disallowed. We use this to
avoid unnecessary overhead when flushing changes to `MDC`.

Being a functional interface, it is also safer to use when exposing state to
outside callers. For example, we can provide a write view over a mutable array,
and decouple that connection when decoding completes.

### Encoding and decoding baggage from remote fields

`BaggageCodec` handles encoding and decoding of baggage fields for remote
propagation. There are some subtle points easy to miss about the design.

* Decode is called before a trace context exists, hence no `context` parameter.
  * First successful decode returns `true`.
    * This allows us to prioritize and leniently read, multiple header names.
  * The `ValueUpdater` parameter allows us to safely collect values from
    multiple contributors.
  * The `request` parameter allows secondary sampling to use `SamplerFunction`
    on inbound requests.
  * The `value` parameter could be a delimited string, or a plain value to set.

* Encode is called only when a baggage exists and isn't all redacted
  * The `Map<String, String>` parameter allows implementations to encode all
    baggage in one string.
    * Not `Map<BaggageField, String>` as we checked all implementations only
      read the field name and non-null values.
  * The `context` parameter allows `BaggageField.getValue` in simple case (more
    efficient than map).
    * The `context` parameter also allows secondary sampling to write down the
      `spanId`
  * The `request` parameter allows secondary sampling to use `SamplerFunction`
    on outbound requests.

#### Why not decode into a Map?
When looking at the encode vs decode side, it might seem curious why they don't
both implement `Map`. `Map` is helpful on the encode side. For example,
implementing  single-header encoding, is easier this way.

```java
joiner = Joiner.on(',').withKeyValueSeparator('=');

String encoded = joiner.join(baggageValues);
```

On the read side, the above iteration is stable because the underlying state is
immutable. There's no chance of a concurrent modification or otherwise
corrupting the encoded value. This applies even when the implementation ignores
the documentation and holds a reference to the map after encoding.

The reverse isn't true. Decoding has different concerns entirely.
* `BaggageField` instances are typically constant, so updates should be a
  function of `BaggageField`, not String.
* There may be multiple decoders contributing to the same baggage state. It is
  more safe underneath to prevent read-ops, such as iterators.
* If we used a `Map`, implementations who ignore the documentation and hold a
  reference to it could later corrupt the state, violating copy-on-write.
  * defending against this is possible, but inefficient and distracting code.

It is true that there are tools that can split common data structures into a
map. To do the same with another function could seem annoying.

Ex.
```java
splitter = Splitter.on(',').withKeyValueSeparator('=');

values = splitter.split(encoded);
```

However, if you look above, this assumes the key type is `String`. Such an
implementation would still need to loop over the result, possibly reuse an
existing `BaggageField`, then .. copy into the correct map!

Ex.
```java
splitter = Splitter.on(',').withKeyValueSeparator('=');

...
for (Entry<String, String> entry: splitter.split(encoded).entrySet()) {
  BaggageField field = lookupOrCreateField(entry.getKey());
  baggageValues.put(field, entry.getValue());
}
```

When put in this view, it isn't much different than just using `ValueUpdater`.
Ex.
```java
splitter = Splitter.on(',').withKeyValueSeparator('=');

...
for (Entry<String, String> entry: splitter.split(encoded).entrySet()) {
  BaggageField field = lookupOrCreateField(entry.getKey());
  valueUpdater.updateValue(field, entry.getValue());
}
```

A closing point is that the above splitter example is contrived, mainly to show
a design weakness in not choosing map.

In practice, encoding formats typically have rules that splitter libraries
weren't written for. These include limits, character set constraints, etc.
Decoding a format is always harder than encoding due to rules about how to
handle bad data. In general, it is better to assume a format-specific decoder
will be needed.

Moreover, decoding through a map is inherently inefficient vs index-based
approaches which do not imply instantiating intermediate objects, nor entry set
views, nor iterators.

In summary, when written well, there's no notable difference in code that
decodes a joined format into a `Map` vs into a `ValueUpdater`. Since the latter
is less complex to handle and less error-prone, we use this type as the input
to the decode function.

3rd parties who strongly desire use of an intermediate map can still do that.
Just when complete, they should use `map.forEach(valueUpdater::updateValue)` or
similar to transfer its contents into the pending baggage state.

### `Map` views over `BaggageField` values
As `Map` is a standard type, it is a safer type to expose to consumers of all
baggage fields. We decided to use an unmodifiable `Map` instead of an internal
type for use cases such as coalescing all baggage into a single header. This
map must also hide local fields from propagation.

#### Why not a standard `Map` type
We considered copying the internal state array to an existing `Map` type, such
as `LinkedHashMap`. However, doing so would add overhead regardless of it that
`Map` was ever used, or if that map had a value for the field the consumer was
interested in! Concretely, we'd pay to create the map, to copy the values in
the array into it, and also pay implicit cost of iterator allocation for
operations such as `entrySet()` even if the entry desired was not present. This
holds present even extending `AbstractMap`, as most operations, even `get()`
allocate iterators.

#### `UnsafeArrayMap`
We created `UnsafeArrayMap` as a view over our pair-wise internal state array.
The implementation is very simple except that we have a redaction use case to
address, which implies filtering keys. To address that, we keep a bitmap of all
filtered keys and consider that when performing any scan operations.

## SpanHandler
There are a few reasons why `SpanHandler` exists. For example:

* We need to manipulate Baggage without overriding `PropagationFactory`, e.g. when
some baggage are derived (parsed from other headers): Given a create hook, one can post process
baggage before the span is visible to the user.
* Another example is parent-child relationships: With a create hook someone can do things like count
children (needed by [stackdriver model](https://cloud.google.com/trace/docs/reference/v2/rpc/google.devtools.cloudtrace.v2#google.devtools.cloudtrace.v2.Span)).
* Some services require the entire "local root" to be reported at the same time. The only
known example of this is [DataDog](https://github.com/DataDog/dd-trace-java/blob/406b324a82b482d7d8ad3faa5f9ccdd307c72308/dd-trace-ot/src/main/java/datadog/trace/common/writer/Writer.java#L20): A create hook can be used track a local root, allowing buffering of all descendants.

### What's with all the termination hooks?
Most users will be unaware that there's an end state besides `Span.finish()`, but here are all
possibilities:

 * `Span.abandon()` - if a speculative context
 * `Span.flush()` - if intentionally reported incomplete
 * `Span.finish()` - normal case
 * "orphan" - a possibly empty span reported incomplete due to garbage collection

The above hooks ensure every created trace context has an end state. This is particularly different
than `SpanHandler`, which doesn't see some nuance such as allocated, but unused contexts,
which would end up orphaned. The cause of these is usually a leak.

## CorrelationScopeDecorator

### Why hold the initial values even when they match?

The value read at the beginning of a scope is currently held when there's a
chance the field can be updated later:

* `CorrelationField.dirty()`
  * Always revert because someone else could have changed it (ex via `MDC`)
* `CorrelationField.flushOnUpdate()`
  * Revert if only when we changed it (ex via `BaggageField.updateValue()`)

This is because users generally expect data to be "cleaned up" when a scope
completes, even if it was written mid-scope.

https://github.com/spring-cloud/spring-cloud-sleuth/issues/1416

If we delayed reading the value, until update, it could be different, due to
nesting of scopes or out-of-band updates to the correlation context. Hence, we
only have one opportunity to read the value: at scope creation time.

### CorrelationContext

The `CorrelationContext` design is based on pragmatism due to what's available
in underlying log contexts. It mainly avoids operations that accept Map as this
implies overhead to construct and iterate over.

Here is an example source from Log4J 2:
```java
public interface ThreadContextMap {
 void clear();

 boolean containsKey(String var1);

 String get(String var1);

 Map<String, String> getCopy();

 Map<String, String> getImmutableMapOrNull();

 boolean isEmpty();

 void put(String var1, String var2);

 void remove(String var1);
}
```

#### Why not guard on previous value when doing an update?

While the current design is optimized for log contexts (or those similar such
as JFR), you can reasonably think of this like generic contexts such as gRPC
and Armeria:
https://github.com/line/armeria/blob/master/core/src/main/java/com/linecorp/armeria/common/RequestContextStorage.java#L88
https://github.com/grpc/grpc-java/blob/master/context/src/main/java/io/grpc/ThreadLocalContextStorage.java

The above include design facets to help with overlapping scopes, notably
comparing the current value vs the one written prior to reverting a value.

There are two main reasons we aren't doing this is in the current
`CorrelationContext` api. First, this feature was designed for contexts which
don't have these operators. To get a compare-and-set outcome would require
reading back the logging context manually. This has two problems, one is
performance impact and the other is that the value can always be updated
out-of-band. Unlike gRPC context, logging contexts are plain string keys, and
easy to clobber by users or other code. It is therefore hard to tell if
inconsistency is due to things under your control or not (ex bad
instrumentation vs 3rd party code).

The other reason is that this is only used internally where we control the
call sites. These call sites are already try/finally in nature, which addresses
the concern we can control.
