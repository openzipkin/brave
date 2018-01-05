package brave.propagation;

import brave.Tracing;
import org.junit.Test;

/** Ensures there's no NPE when tracing builder uses defaults */
public class PropagationConstantsTest {

  @Test public void eagerReferencePropagationConstantPriorToUse() {
    Propagation<String> foo = Propagation.B3_STRING;
    Tracing.newBuilder().build().close();
  }
}
