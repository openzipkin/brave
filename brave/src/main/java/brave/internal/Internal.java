package brave.internal;

import brave.Tracer;
import brave.propagation.TraceContext;
import javax.annotation.Nullable;

/**
 * Allows internal classes outside the package {@code brave} to use non-public methods. This allows
 * us access internal methods while also making obvious the hooks are not for public use. The only
 * implementation of this interface is in {@link brave.Tracer}.
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.Internal}
 */
public abstract class Internal {

  // Used by Brave 3 apis
  public abstract @Nullable Long timestamp(Tracer tracer, TraceContext context);

  public static Internal instance;
}
