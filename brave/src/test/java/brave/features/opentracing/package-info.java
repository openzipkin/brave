/**
 * It was difficult to implement OpenTracing directly within Brave 3, particularly as spans were
 * spread across 3 different tracing abstractions. Now, {@link brave.Span} can implement {@link
 * io.opentracing.Span} directly, at least the features that can be mapped to Zipkin.
 */
package brave.features.opentracing;
