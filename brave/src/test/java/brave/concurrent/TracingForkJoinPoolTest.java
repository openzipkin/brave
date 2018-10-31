package brave.concurrent;

import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import java.util.concurrent.ForkJoinPool;
import org.junit.Test;

public class TracingForkJoinPoolTest {

  CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.create();
  ForkJoinPool forkJoinPool = TracingForkJoinPool.wrap(currentTraceContext, null, null, null, false);

  @Test
  public void execute() throws Exception {
    forkJoinPool.execute(new Runnable() {
      @Override public void run() {

      }
    });
  }

}
