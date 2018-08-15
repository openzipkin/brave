package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Function;
import io.reactivex.internal.functions.Functions;
import io.reactivex.internal.fuseable.ScalarCallable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.parallel.ParallelFlowable;
import io.reactivex.plugins.RxJavaPlugins;
import java.util.concurrent.Callable;

/**
 * Prevents traces from breaking during RxJava operations by scoping them with trace context.
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
    if (oldCompletable == null || !chain) {
      oldCompletable = Functions.identity();
    }
    final Function<? super Completable, ? extends Completable> oldC = oldCompletable;

    RxJavaPlugins.setOnCompletableAssembly(
        new ConditionalOnCurrentTraceContextFunction<Completable>() {
          @Override
          Completable applyActual(Completable c, TraceContext ctx) {
            if (!(c instanceof Callable)) {
              return new TraceContextCompletable(c, currentTraceContext, ctx);
            }
            if (c instanceof ScalarCallable) {
              return new TraceContextScalarCallableCompletable(c, currentTraceContext, ctx);
            }
            return new TraceContextCallableCompletable(c, currentTraceContext, ctx);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super Maybe, ? extends Maybe> saveM = RxJavaPlugins.getOnMaybeAssembly();
    Function<? super Maybe, ? extends Maybe> oldMaybe = saveM;
    if (oldMaybe == null || !chain) {
      oldMaybe = Functions.identity();
    }
    final Function<? super Maybe, ? extends Maybe> oldM = oldMaybe;

    RxJavaPlugins.setOnMaybeAssembly(
        new ConditionalOnCurrentTraceContextFunction<Maybe>() {
          @Override
          Maybe applyActual(Maybe m, TraceContext ctx) {
            if (!(m instanceof Callable)) {
              return new TraceContextMaybe(m, currentTraceContext, ctx);
            }
            if (m instanceof ScalarCallable) {
              return new TraceContextScalarCallableMaybe(m, currentTraceContext, ctx);
            }
            return new TraceContextCallableMaybe(m, currentTraceContext, ctx);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super Single, ? extends Single> saveS = RxJavaPlugins.getOnSingleAssembly();
    Function<? super Single, ? extends Single> oldSingle = saveS;
    if (oldSingle == null || !chain) {
      oldSingle = Functions.identity();
    }
    final Function<? super Single, ? extends Single> oldS = oldSingle;

    RxJavaPlugins.setOnSingleAssembly(
        new ConditionalOnCurrentTraceContextFunction<Single>() {
          @Override
          Single applyActual(Single s, TraceContext ctx) {
            if (!(s instanceof Callable)) {
              return new TraceContextSingle(s, currentTraceContext, ctx);
            }
            if (s instanceof ScalarCallable) {
              return new TraceContextScalarCallableSingle(s, currentTraceContext, ctx);
            }
            return new TraceContextCallableSingle(s, currentTraceContext, ctx);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super Observable, ? extends Observable> saveO =
        RxJavaPlugins.getOnObservableAssembly();
    Function<? super Observable, ? extends Observable> oldObservable = saveO;
    if (oldObservable == null || !chain) {
      oldObservable = Functions.identity();
    }
    final Function<? super Observable, ? extends Observable> oldO = oldObservable;

    RxJavaPlugins.setOnObservableAssembly(
        new ConditionalOnCurrentTraceContextFunction<Observable>() {
          @Override
          Observable applyActual(Observable o, TraceContext ctx) {
            if (!(o instanceof Callable)) {
              return new TraceContextObservable(o, currentTraceContext, ctx);
            }
            if (o instanceof ScalarCallable) {
              return new TraceContextScalarCallableObservable(o, currentTraceContext, ctx);
            }
            return new TraceContextCallableObservable(o, currentTraceContext, ctx);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super Flowable, ? extends Flowable> saveF =
        RxJavaPlugins.getOnFlowableAssembly();
    Function<? super Flowable, ? extends Flowable> oldFlowable = saveF;
    if (oldFlowable == null || !chain) {
      oldFlowable = Functions.identity();
    }
    final Function<? super Flowable, ? extends Flowable> oldF = oldFlowable;

    RxJavaPlugins.setOnFlowableAssembly(
        new ConditionalOnCurrentTraceContextFunction<Flowable>() {
          @Override
          Flowable applyActual(Flowable f, TraceContext ctx) {
            if (!(f instanceof Callable)) {
              return new TraceContextFlowable(f, currentTraceContext, ctx);
            }
            if (f instanceof ScalarCallable) {
              return new TraceContextScalarCallableFlowable(f, currentTraceContext, ctx);
            }
            return new TraceContextCallableFlowable(f, currentTraceContext, ctx);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super ConnectableFlowable, ? extends ConnectableFlowable> saveCF =
        RxJavaPlugins.getOnConnectableFlowableAssembly();
    Function<? super ConnectableFlowable, ? extends ConnectableFlowable> oldConnFlow = saveCF;
    if (oldConnFlow == null || !chain) {
      oldConnFlow = Functions.identity();
    }
    final Function<? super ConnectableFlowable, ? extends ConnectableFlowable> oldCF = oldConnFlow;

    RxJavaPlugins.setOnConnectableFlowableAssembly(
        new ConditionalOnCurrentTraceContextFunction<ConnectableFlowable>() {
          @Override
          ConnectableFlowable applyActual(ConnectableFlowable cf, TraceContext ctx) {
            return new TraceContextConnectableFlowable(cf, currentTraceContext, ctx);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super ConnectableObservable, ? extends ConnectableObservable> saveCO =
        RxJavaPlugins.getOnConnectableObservableAssembly();
    Function<? super ConnectableObservable, ? extends ConnectableObservable> oldConnObs = saveCO;
    if (oldConnObs == null || !chain) {
      oldConnObs = Functions.identity();
    }
    final Function<? super ConnectableObservable, ? extends ConnectableObservable> oldCO =
        oldConnObs;

    RxJavaPlugins.setOnConnectableObservableAssembly(
        new ConditionalOnCurrentTraceContextFunction<ConnectableObservable>() {
          @Override
          ConnectableObservable applyActual(ConnectableObservable co, TraceContext ctx) {
            return new TraceContextConnectableObservable(co, currentTraceContext, ctx);
          }
        });

    // ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

    final Function<? super ParallelFlowable, ? extends ParallelFlowable> savePF =
        RxJavaPlugins.getOnParallelAssembly();
    Function<? super ParallelFlowable, ? extends ParallelFlowable> oldParFlow = savePF;
    if (oldParFlow == null || !chain) {
      oldParFlow = Functions.identity();
    }
    final Function<? super ParallelFlowable, ? extends ParallelFlowable> oldPF = oldParFlow;

    RxJavaPlugins.setOnParallelAssembly(
        new ConditionalOnCurrentTraceContextFunction<ParallelFlowable>() {
          @Override
          ParallelFlowable applyActual(ParallelFlowable pf, TraceContext ctx) {
            return new TraceContextParallelFlowable(pf, currentTraceContext, ctx);
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

  /** Disables the validation hooks be resetting the assembly hooks to none. */
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

  abstract class ConditionalOnCurrentTraceContextFunction<T> implements Function<T, T> {
    @Override
    public final T apply(T t) {
      TraceContext ctx = currentTraceContext.get();
      if (ctx == null) return t; // less overhead when there's no current trace
      return applyActual(t, ctx);
    }

    abstract T applyActual(T t, TraceContext ctx);
  }
}
