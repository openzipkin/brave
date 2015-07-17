package com.github.kristofa.brave.http;


public class StringServiceNameProvider implements ServiceNameProvider {

    private final String serviceName;

    public StringServiceNameProvider(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public String serviceName(HttpRequest request) {
        return serviceName;
    }
}
