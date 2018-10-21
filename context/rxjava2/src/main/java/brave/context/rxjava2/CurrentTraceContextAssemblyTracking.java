package brave.context.rxjava2;

import brave.context.rxjava2.internal.Util;
import brave.context.rxjava2.internal.fuseable.MaybeFuseable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Function;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.parallel.ParallelFlowable;
import io.reactivex.plugins.RxJavaPlugins;
import java.util.concurrent.Callable;

/**
 * Prevents traces from breaking during RxJava operations by scoping trace context that existed at
 * assembly time around callbacks or computation of new values.
 *
 * <p>The design of this library borrows heavily from https://github.com/akaita/RxJava2Debug and
 * https://github.com/akarnokd/RxJava2Extensions
 */
public final class CurrentTraceContextAssemblyTracking {

  public interface SavedHooks {

    /** Restores the previous set of hooks. */
    void restore();
  }

  static volatile boolean enabled;

  final CurrentTraceContext currentTraceContext;

  CurrentTraceContextAssemblyTracking(CurrentTraceContext currentTraceContext) {
    if (currentTraceContext == null) throw new NullPointerException("currentTraceContext == null");
    this.currentTraceContext = currentTraceContext;
  }

  public static CurrentTraceContextAssemblyTracking create(CurrentTraceContext delegate) {
    return new CurrentTraceContextAssemblyTracking(delegate);
  }

  /**
   * Enable the protocol violation hooks.
   *
   * @see #enableAndChain()
   * @see #disable()
   */
  public void enable() {
    enable(false);
  }

  /**
   * Enable the protocol violation hooks by chaining it before any existing hook.
   *
   * @return the SavedHooks instance that allows restoring the previous assembly hook handlers
   * overridden by this method
   * @see #enable()
   */
  public SavedHooks enableAndChain() {
    return enable(true);
  }

  @SuppressWarnings("rawtypes")
  SavedHooks enable(boolean chain) {
    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super Completable, ? extends Completable> saveC =
        RxJavaPlugins.getOnCompletableAssembly();
    Function<? super Completable, ? extends Completable> oldCompletable = saveC;
    if (oldCompletable == null || !chain) oldCompletable = Util.identity();

    RxJavaPlugins.setOnCompletableAssembly(
        new ConditionalOnCurrentTraceContextFunction<Completable>(oldCompletable) {
          @Override Completable applyActual(Completable c, TraceContext assembled) {
            if (!(c instanceof Callable)) {
              return new TraceContextCompletable(c, currentTraceContext, assembled);
            }
            return MaybeFuseable.get().wrap(c, currentTraceContext, assembled);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super Maybe, ? extends Maybe> saveM = RxJavaPlugins.getOnMaybeAssembly();
    Function<? super Maybe, ? extends Maybe> oldMaybe = saveM;
    if (oldMaybe == null || !chain) oldMaybe = Util.identity();

    RxJavaPlugins.setOnMaybeAssembly(
        new ConditionalOnCurrentTraceContextFunction<Maybe>(oldMaybe) {
          @Override Maybe applyActual(Maybe m, TraceContext assembled) {
            if (!(m instanceof Callable)) {
              return new TraceContextMaybe(m, currentTraceContext, assembled);
            }
            return MaybeFuseable.get().wrap(m, currentTraceContext, assembled);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super Single, ? extends Single> saveS = RxJavaPlugins.getOnSingleAssembly();
    Function<? super Single, ? extends Single> oldSingle = saveS;
    if (oldSingle == null || !chain) oldSingle = Util.identity();

    RxJavaPlugins.setOnSingleAssembly(
        new ConditionalOnCurrentTraceContextFunction<Single>(oldSingle) {
          @Override Single applyActual(Single s, TraceContext assembled) {
            if (!(s instanceof Callable)) {
              return new TraceContextSingle(s, currentTraceContext, assembled);
            }
            return MaybeFuseable.get().wrap(s, currentTraceContext, assembled);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super Observable, ? extends Observable> saveO =
        RxJavaPlugins.getOnObservableAssembly();
    Function<? super Observable, ? extends Observable> oldObservable = saveO;
    if (oldObservable == null || !chain) oldObservable = Util.identity();

    RxJavaPlugins.setOnObservableAssembly(
        new ConditionalOnCurrentTraceContextFunction<Observable>(oldObservable) {
          @Override Observable applyActual(Observable o, TraceContext assembled) {
            if (!(o instanceof Callable)) {
              return new TraceContextObservable(o, currentTraceContext, assembled);
            }
            return MaybeFuseable.get().wrap(o, currentTraceContext, assembled);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super Flowable, ? extends Flowable> saveF =
        RxJavaPlugins.getOnFlowableAssembly();
    Function<? super Flowable, ? extends Flowable> oldFlowable = saveF;
    if (oldFlowable == null || !chain) oldFlowable = Util.identity();

    RxJavaPlugins.setOnFlowableAssembly(
        new ConditionalOnCurrentTraceContextFunction<Flowable>(oldFlowable) {
          @Override Flowable applyActual(Flowable f, TraceContext assembled) {
            if (!(f instanceof Callable)) {
              return new TraceContextFlowable(f, currentTraceContext, assembled);
            }
            return MaybeFuseable.get().wrap(f, currentTraceContext, assembled);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super ConnectableFlowable, ? extends ConnectableFlowable> saveCF =
        RxJavaPlugins.getOnConnectableFlowableAssembly();
    Function<? super ConnectableFlowable, ? extends ConnectableFlowable> oldConnFlow = saveCF;
    if (oldConnFlow == null || !chain) oldConnFlow = Util.identity();

    RxJavaPlugins.setOnConnectableFlowableAssembly(
        new ConditionalOnCurrentTraceContextFunction<ConnectableFlowable>(oldConnFlow) {
          @Override ConnectableFlowable applyActual(ConnectableFlowable cf,
              TraceContext assembled) {
            return new TraceContextConnectableFlowable(cf, currentTraceContext, assembled);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super ConnectableObservable, ? extends ConnectableObservable> saveCO =
        RxJavaPlugins.getOnConnectableObservableAssembly();
    Function<? super ConnectableObservable, ? extends ConnectableObservable> oldConnObs = saveCO;
    if (oldConnObs == null || !chain) oldConnObs = Util.identity();

    RxJavaPlugins.setOnConnectableObservableAssembly(
        new ConditionalOnCurrentTraceContextFunction<ConnectableObservable>(oldConnObs) {
          @Override ConnectableObservable applyActual(ConnectableObservable co,
              TraceContext assembled) {
            return new TraceContextConnectableObservable(co, currentTraceContext, assembled);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super ParallelFlowable, ? extends ParallelFlowable> savePF =
        RxJavaPlugins.getOnParallelAssembly();
    Function<? super ParallelFlowable, ? extends ParallelFlowable> oldParFlow = savePF;
    if (oldParFlow == null || !chain) oldParFlow = Util.identity();

    RxJavaPlugins.setOnParallelAssembly(
        new ConditionalOnCurrentTraceContextFunction<ParallelFlowable>(oldParFlow) {
          @Override ParallelFlowable applyActual(ParallelFlowable pf, TraceContext assembled) {
            return new TraceContextParallelFlowable(pf, currentTraceContext, assembled);
          }
        });

    enabled = true;

    return new SavedHooks() {
      @Override
      public void restore() {
        RxJavaPlugins.setOnCompletableAssembly(saveC);
        RxJavaPlugins.setOnSingleAssembly(saveS);
        RxJavaPlugins.setOnMaybeAssembly(saveM);
        RxJavaPlugins.setOnObservableAssembly(saveO);
        RxJavaPlugins.setOnFlowableAssembly(saveF);

        RxJavaPlugins.setOnConnectableObservableAssembly(saveCO);
        RxJavaPlugins.setOnConnectableFlowableAssembly(saveCF);

        RxJavaPlugins.setOnParallelAssembly(savePF);
      }
    };
  }

  /** Disables the validation hooks be resetting the assembled hooks to none. */
  public static void disable() {
    RxJavaPlugins.setOnCompletableAssembly(null);
    RxJavaPlugins.setOnSingleAssembly(null);
    RxJavaPlugins.setOnMaybeAssembly(null);
    RxJavaPlugins.setOnObservableAssembly(null);
    RxJavaPlugins.setOnFlowableAssembly(null);

    RxJavaPlugins.setOnConnectableObservableAssembly(null);
    RxJavaPlugins.setOnConnectableFlowableAssembly(null);

    RxJavaPlugins.setOnParallelAssembly(null);
    enabled = false;
  }

  /** Returns true if the validation hooks have been installed. */
  public static boolean isEnabled() {
    return enabled;
  }

  /**
   * This is the only code that gets the assembly time trace context. Wrapped code applies this
   * assembled context at runtime with {@link CurrentTraceContext#newScope(TraceContext)}.
   */
  abstract class ConditionalOnCurrentTraceContextFunction<T> implements Function<T, T> {
    final Function<? super T, ? extends T> oldFn;

    ConditionalOnCurrentTraceContextFunction(Function<? super T, ? extends T> oldFn) {
      this.oldFn = oldFn;
    }

    @Override public final T apply(T t) throws Exception {
      TraceContext assembled = currentTraceContext.get();
      if (assembled == null) return oldFn.apply(t); // less overhead when there's no current trace
      return applyActual(oldFn.apply(t), assembled);
    }

    abstract T applyActual(T t, TraceContext assembled);
  }

  static {
    Internal.instance = new Internal() {
      @Override public <T> Observer<T> wrap(Observer<T> downstream,
          CurrentTraceContext contextScoper, TraceContext assembled) {
        return new TraceContextObserver<>(downstream, contextScoper, assembled);
      }

      @Override public <T> SingleObserver<T> wrap(SingleObserver<T> downstream,
          CurrentTraceContext contextScoper, TraceContext assembled) {
        return new TraceContextSingleObserver<>(downstream, contextScoper, assembled);
      }

      @Override public <T> MaybeObserver<T> wrap(MaybeObserver<T> downstream,
          CurrentTraceContext contextScoper, TraceContext assembled) {
        return new TraceContextMaybeObserver<>(downstream, contextScoper, assembled);
      }

      @Override public CompletableObserver wrap(CompletableObserver downstream,
          CurrentTraceContext contextScoper, TraceContext assembled) {
        return new TraceContextCompletableObserver(downstream, contextScoper, assembled);
      }
    };
  }
}
