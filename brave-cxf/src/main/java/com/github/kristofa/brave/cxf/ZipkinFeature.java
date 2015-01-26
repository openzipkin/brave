package com.github.kristofa.brave.cxf;

import org.apache.cxf.Bus;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.interceptor.InterceptorProvider;

/**
 * Created by fedor on 13.01.15.
 */

public class ZipkinFeature extends AbstractFeature {
    @Override
    protected void initializeProvider(InterceptorProvider provider, Bus bus) {
        ZipkinConfig.InstallCXFZipkinInterceptors(provider);
    }
}
