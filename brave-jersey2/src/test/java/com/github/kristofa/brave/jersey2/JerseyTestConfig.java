package com.github.kristofa.brave.jersey2;

import org.glassfish.jersey.server.ResourceConfig;

public class JerseyTestConfig extends ResourceConfig {

    public JerseyTestConfig() {
        packages("com.github.kristofa.brave.jersey2");
    }

}
