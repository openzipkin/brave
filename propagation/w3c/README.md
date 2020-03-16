# brave-propagation-w3c

This project includes propagation handlers for W3C defined headers.

## Trace Context
The [Trace Context][https://w3c.github.io/trace-context/] specification defines two headers:

 * `traceparent` - almost the same as our [B3-single format](https://github.com/openzipkin/b3-propagation#single-header)
 * `tracestate` - vendor-specific (or format-specific): may impact how to interpret `traceparent`

This implementation can survive mixed systems who follow the specification and forward the
`tracestate` header. When writing the `traceparent` header, this also overwrites the `tracestate`
entry named 'b3' (in B3 single format). When reading headers, this entry is favored over the
`traceparent`, allowing the the next span to re-attach to the last known 'b3' header.
