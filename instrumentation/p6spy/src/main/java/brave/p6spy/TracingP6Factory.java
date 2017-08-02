package brave.p6spy;

import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.spy.P6Factory;
import com.p6spy.engine.spy.P6LoadableOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;

/** Add this class name to the "moduleslist" in spy.properties */
public final class TracingP6Factory implements P6Factory {

  TracingP6SpyOptions options;

  @Override public P6LoadableOptions getOptions(P6OptionsRepository repository) {
    return options = new TracingP6SpyOptions(repository);
  }

  @Override public JdbcEventListener getJdbcEventListener() {
    return new TracingJdbcEventListener(options.remoteServiceName(), options.includeParameterValues());
  }
}
