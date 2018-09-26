package brave.extensions.java7.propagation;

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

public final class TracingForkJoinPoolThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {

  Tracing tracing;

  public TracingForkJoinPoolThreadFactory(Tracing tracing) {
    this.tracing = tracing;
  }

  @Override public ForkJoinWorkerThread newThread(ForkJoinPool forkJoinPool) {
    return new WrappingForkJoinWorkerThread(forkJoinPool, tracing.currentTraceContext());
  }

  final class WrappingForkJoinWorkerThread extends ForkJoinWorkerThread {
    CurrentTraceContext currentTraceContext;
    TraceContext traceContext;

    WrappingForkJoinWorkerThread(ForkJoinPool pool, CurrentTraceContext currentTraceContext) {
      super(pool);
      this.currentTraceContext = currentTraceContext;
      this.traceContext = currentTraceContext.get();
    }

    @Override public ForkJoinPool getPool() {
      return super.getPool();
    }

    @Override public int getPoolIndex() {
      return super.getPoolIndex();
    }

    @Override protected void onStart() {
      super.onStart();
    }

    @Override protected void onTermination(Throwable exception) {
      super.onTermination(exception);
    }

    @Override public void run() {
      try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(traceContext)) {
        super.run();
      }
    }
  }
}
