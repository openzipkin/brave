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

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fedor on 12.01.15.
 */
public class ZipkinConfig {

    final protected static SpanCollector zipkinSpanCollector;

    static {
        //zipkinSpanCollector = new ZipkinSpanCollector("localhost", 9410);
        zipkinSpanCollector = new LoggingSpanCollectorImpl();
    }

    public static void InstallCXFZipkinInterceptors(InterceptorProvider provider){
        //It is Ok to recreate those objects on any accasion, those classes do not have own state
        //and just decorate Brave state class
        final List<TraceFilter> traceFilters = new ArrayList<TraceFilter>();
        final ClientTracer clientTracer = Brave.getClientTracer(zipkinSpanCollector, traceFilters);
        final ServerTracer serverTracer = Brave.getServerTracer(zipkinSpanCollector, traceFilters);
        final EndPointSubmitter endPointSubmitter = Brave.getEndPointSubmitter();

        final InZipkinInterceptor inZipkinInterceptor = new InZipkinInterceptor(endPointSubmitter, clientTracer, serverTracer);
        provider.getInInterceptors().add(inZipkinInterceptor);

        final OutZipkinInterceptor outZipkinInterceptor = new OutZipkinInterceptor(clientTracer, serverTracer, Optional.fromNullable("dummy-service"));
        provider.getOutInterceptors().add(outZipkinInterceptor);
    }

}

