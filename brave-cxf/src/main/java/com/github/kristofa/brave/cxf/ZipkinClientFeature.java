package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.cxf.ZipkinConfig;
import org.apache.cxf.Bus;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.interceptor.InterceptorProvider;

/**
 * Created by fedor on 26.01.15.
 */

public class ZipkinClientFeature extends AbstractFeature {
    @Override
    protected void initializeProvider(InterceptorProvider provider, Bus bus) {
        ZipkinConfig.InstallClientCXFZipkinInterceptors(provider);
    }
}
