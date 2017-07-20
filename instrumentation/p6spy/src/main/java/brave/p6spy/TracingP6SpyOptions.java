package brave.p6spy;

import com.p6spy.engine.spy.P6SpyOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;
import java.util.Map;

final class TracingP6SpyOptions extends P6SpyOptions {
  static final String REMOTE_SERVICE_NAME = "remoteServiceName";
  static final String LOG_JDBC_PARAMETER_VALUES = "logJdbcParameterValues";

  final P6OptionsRepository optionsRepository;

  TracingP6SpyOptions(P6OptionsRepository optionsRepository) {
    super(optionsRepository);
    this.optionsRepository = optionsRepository;
  }

  @Override public void load(Map<String, String> options) {
    super.load(options);
    optionsRepository.set(String.class, REMOTE_SERVICE_NAME, options.get(REMOTE_SERVICE_NAME));
    optionsRepository.set(Boolean.class, LOG_JDBC_PARAMETER_VALUES, options.get(LOG_JDBC_PARAMETER_VALUES));
  }

  String remoteServiceName() {
    return optionsRepository.get(String.class, REMOTE_SERVICE_NAME);
  }

  Boolean logSqlWithParameterValues() {
    Boolean logParameterValues = optionsRepository.get(Boolean.class, LOG_JDBC_PARAMETER_VALUES);
    return logParameterValues == null ? false : logParameterValues;
  }
}
