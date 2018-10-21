package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.CompletableObserver;
import io.reactivex.MaybeObserver;
import io.reactivex.Observer;
import io.reactivex.SingleObserver;

/**
 * Escalate internal APIs in {@code brave.context.rxjava2} so they can be used from outside
 * packages. The only implementation is in {@link CurrentTraceContextAssemblyTracking}.
 *
 * <p>Inspired by {@code okhttp3.internal.Internal}.
 */
public abstract class Internal {
  public static Internal instance;

  public abstract <T> Observer<T> wrap(
      Observer<T> downstream, CurrentTraceContext contextScoper, TraceContext assembled);

  public abstract <T> SingleObserver<T> wrap(
      SingleObserver<T> downstream, CurrentTraceContext contextScoper, TraceContext assembled);

  public abstract <T> MaybeObserver<T> wrap(
      MaybeObserver<T> downstream, CurrentTraceContext contextScoper, TraceContext assembled);

  public abstract CompletableObserver wrap(
      CompletableObserver downstream, CurrentTraceContext contextScoper, TraceContext assembled);

  Internal() {
  }
}
