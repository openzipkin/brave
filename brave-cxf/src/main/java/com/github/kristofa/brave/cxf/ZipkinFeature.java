package com.github.kristofa.brave.cxf;

import org.apache.cxf.Bus;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.interceptor.InterceptorProvider;
import org.springframework.beans.factory.annotation.Required;

/**
 * Created by fedor on 13.01.15.
 */

public class ZipkinFeature extends AbstractFeature {

    private boolean useLogging = false;
    private boolean useZipkin = false;
    private String collectorHost = null;
    private int collectorPort = 0;
    private String clientServiceName = null;

    private ZipkinConfig zipkinConfig;

    public void setUseLogging(boolean useLogging) {
        this.useLogging = useLogging;
    }

    public void setUseZipkin(boolean useZipkin) {
        this.useZipkin = useZipkin;
    }

    public void setCollectorHost(String collectorHost) {
        this.collectorHost = collectorHost;
    }

    public void setCollectorPort(int collectorPort) {
        this.collectorPort = collectorPort;
    }

    public void setClientServiceName(String clientServiceName) {
        this.clientServiceName = clientServiceName;
    }

    private ZipkinConfig getZipkinConfig()
    {
        if (zipkinConfig != null)
            return zipkinConfig;
        zipkinConfig = new ZipkinConfig(useLogging, useZipkin, collectorHost, collectorPort, clientServiceName);
        return zipkinConfig;
    }

    @Override
    protected void initializeProvider(InterceptorProvider provider, Bus bus) {
        getZipkinConfig().InstallCXFZipkinInterceptors(provider);
    }
}
