/**
 * one-way, async or messaging spans are possible when you {@link brave.Span#flush()} a span after
 * sending on one host and flush after receiving on the other. This requires an annotation on either
 * side. Zipkin will backfill the transit time using the annotation timestamps.
 */
package brave.features.async;
