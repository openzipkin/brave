package com.github.kristofa.brave.p6spy;

import com.p6spy.engine.spy.P6LoadableOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;

import java.util.Collections;
import java.util.Map;

import javax.management.StandardMBean;

/**
 * {@link P6LoadableOptions} for the Brave listener. P6 forces all options classes to be MBean's so the getters
 * are available to view.
 */
final class P6BraveOptions extends StandardMBean implements P6LoadableOptions, P6BraveOptionsMBean {

    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String SERVICE_NAME = "serviceName";

    private final P6OptionsRepository optionsRepository;

    public P6BraveOptions(final P6OptionsRepository optionsRepository) {
        super(P6BraveOptionsMBean.class, false);
        this.optionsRepository = optionsRepository;
    }

    @Override
    public void load(Map<String, String> options) {
        setHost(options.get(HOST));
        setPort(options.get(PORT));
        setServiceName(options.get(SERVICE_NAME));
    }

    @Override
    public Map<String, String> getDefaults() {
        return Collections.emptyMap();
    }

    @Override
    public String getHost() {
      return optionsRepository.get(String.class, HOST);
    }

    public void setHost(String host) {
      optionsRepository.set(String.class, HOST, host);
    }

    @Override
    public String getPort() {
      return optionsRepository.get(String.class, PORT);
    }

    public void setPort(String port) {
      optionsRepository.set(String.class, PORT, port);
    }

    @Override
    public String getServiceName() {
      return optionsRepository.get(String.class, SERVICE_NAME);
    }

    public void setServiceName(String serviceName) {
      optionsRepository.set(String.class, SERVICE_NAME, serviceName);
    }

}
