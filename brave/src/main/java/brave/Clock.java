package brave;

/**
 * Epoch microseconds used for {@link zipkin2.Span#timestamp} and {@link
 * zipkin2.Annotation#timestamp}.
 *
 * <p>This should use the most precise value possible. For example, {@code gettimeofday} or
 * multiplying {@link System#currentTimeMillis} by 1000.
 *
 * <p>See <a href="http://zipkin.io/pages/instrumenting.html">Instrumenting a service</a> for
 * more.
 */
// FunctionalInterface except Java language level 6
public interface Clock {

  long currentTimeMicroseconds();
}
