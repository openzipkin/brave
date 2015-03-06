package com.github.kristofa.brave.cxf;

import org.apache.cxf.Bus;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.interceptor.InterceptorProvider;

/**
 * Created by fedor on 13.01.15.
 */

public class ZipkinFeature extends AbstractFeature {

    private boolean useLogging = false;
    private boolean useZipkin = false;
    private boolean isRoot = false;
    private String collectorHost = null;
    private int collectorPort = 0;
    private String serviceName = null;

    private ZipkinConfig zipkinConfig;

    public void setUseLogging(boolean useLogging) {

        this.useLogging = useLogging;
        if (useLogging)
            useZipkin = false;
    }

    public void setUseZipkin(boolean useZipkin) {

        this.useZipkin = useZipkin;
        if (useZipkin)
            useLogging = false;
    }

    public void setIsRoot(boolean isRoot) {
        this.isRoot = isRoot;
    }

    public void setCollectorHost(String collectorHost) {
        this.collectorHost = collectorHost;
    }

    public void setCollectorPort(int collectorPort) {
        this.collectorPort = collectorPort;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    private ZipkinConfig getZipkinConfig() {
        if (zipkinConfig != null)
            return zipkinConfig;
        zipkinConfig = new ZipkinConfig(useLogging, useZipkin, isRoot, collectorHost, collectorPort, serviceName);
        return zipkinConfig;
    }

    public void StartRoot(String requestName, String ip, int port) {
        getZipkinConfig().StartRoot(requestName, ip, port);
    }

    public void StartRoot(String requestName) {
        getZipkinConfig().StartRoot(requestName);
    }

    public void DoneRoot() {
        getZipkinConfig().DoneRoot();
    }

    @Override
    protected void initializeProvider(InterceptorProvider provider, Bus bus) {

        getZipkinConfig().InstallCXFZipkinInterceptors(provider);

    }
}
