package brave;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CurrentTracerTest {
  @Before
  public void reset() {
    Tracer.current = null;
  }

  @Test public void defaultsToNull() {
    assertThat(Tracer.current()).isNull();
  }

  @Test public void autoRegisters() {
    Tracer tracer = Tracer.newBuilder().build();

    assertThat(Tracer.current())
        .isSameAs(tracer);
  }

  @Test public void setsNotCurrentOnClose() {
    autoRegisters();

    Tracer.current().close();

    assertThat(Tracer.current()).isNull();
  }

  @Test public void canSetCurrentAgain() {
    setsNotCurrentOnClose();

    autoRegisters();
  }

  @Test public void onlyRegistersOnce() throws InterruptedException {
    final Tracer[] threadValues = new Tracer[10]; // array ref for thread-safe setting

    List<Thread> getOrSet = new ArrayList<>(20);

    for (int i = 0; i < 10; i++) {
      final int index = i;
      getOrSet.add(new Thread(() -> threadValues[index] = Tracer.current()));
    }
    for (int i = 10; i < 20; i++) {
      getOrSet.add(new Thread(() -> Tracer.newBuilder().build()));
    }

    // make it less predictable
    Collections.shuffle(getOrSet);

    // start the races
    getOrSet.forEach(Thread::start);
    for (Thread thread : getOrSet) {
      thread.join();
    }

    Set<Tracer> tracers = new LinkedHashSet<>(Arrays.asList(threadValues));
    tracers.remove(null);
    // depending on race, we should have either one tracer or no tracer
    assertThat(tracers.isEmpty() || tracers.size() == 1)
        .isTrue();
  }
}
