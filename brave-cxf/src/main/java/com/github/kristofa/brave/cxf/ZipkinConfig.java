package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.TraceFilter;
import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.zipkin.ZipkinSpanCollector;
import com.github.kristofa.brave.LoggingSpanCollectorImpl;
import org.apache.cxf.interceptor.InterceptorProvider;
import com.google.common.base.Optional;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fedor on 12.01.15.
 */
public class ZipkinConfig {

    protected SpanCollector spanCollector;
    protected String clientServiceName;

    public ZipkinConfig(boolean useLogging, boolean useZipkin, String collectorHost, int collectorPort, String clientServiceName) {
        if (useLogging) {
            spanCollector = new LoggingSpanCollectorImpl();
        } else if (useZipkin) {
            if (collectorHost == null || collectorHost.isEmpty())
                collectorHost = "locahost";
            if (collectorPort <= 0)
                collectorPort = 9410;
            spanCollector = new ZipkinSpanCollector(collectorHost, collectorPort);
        }
        this.clientServiceName = clientServiceName;
    }

    public void InstallCXFZipkinInterceptors(InterceptorProvider provider){
        //It is Ok to recreate those objects on any accasion, those classes do not have own state
        //and just decorate Brave state class
        if (spanCollector == null)
            return; //No tracing!

        final List<TraceFilter> traceFilters = new ArrayList<TraceFilter>();
        final ClientTracer clientTracer = Brave.getClientTracer(spanCollector, traceFilters);
        final ServerTracer serverTracer = Brave.getServerTracer(spanCollector, traceFilters);
        final EndPointSubmitter endPointSubmitter = Brave.getEndPointSubmitter();

        final InZipkinInterceptor inZipkinInterceptor = new InZipkinInterceptor(endPointSubmitter, clientTracer, serverTracer);
        provider.getInInterceptors().add(inZipkinInterceptor);

        final OutZipkinInterceptor outZipkinInterceptor =
                new OutZipkinInterceptor(clientTracer, serverTracer, Optional.fromNullable(clientServiceName));
        provider.getOutInterceptors().add(outZipkinInterceptor);
    }

}

