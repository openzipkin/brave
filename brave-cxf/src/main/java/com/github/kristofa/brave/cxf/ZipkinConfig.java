package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;
import com.github.kristofa.brave.zipkin.ZipkinSpanCollector;
import com.github.kristofa.brave.zipkin.ZipkinSpanCollectorParams;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.interceptor.InterceptorProvider;
import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fedor on 12.01.15.
 */
public class ZipkinConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ZipkinConfig.class);

    protected final SpanCollector spanCollector;
    protected final String serviceName;

    protected final static AttemptLimiter inAttemptLimiter = new ExpBackoffAttemptLimiter();
    protected final static AttemptLimiter outAttemptLimiter = new ExpBackoffAttemptLimiter();

    protected final List<TraceFilter> traceFilters = new ArrayList<TraceFilter>();
    protected boolean isRoot = false;

    protected volatile boolean rootInitialized = false;

    public ZipkinConfig(boolean useLogging, boolean useZipkin, boolean isRoot,
                        String collectorHost, int collectorPort, String serviceName) {
        if (useLogging) {
            spanCollector = new LoggingSpanCollectorImpl();
        } else if (useZipkin) {
            if (collectorHost == null || collectorHost.isEmpty())
                collectorHost = "locahost";
            if (collectorPort <= 0)
                collectorPort = 9410;
            ZipkinSpanCollectorParams params = new ZipkinSpanCollectorParams();
            params.setFailOnSetup(false);
            spanCollector = new ZipkinSpanCollector(collectorHost, collectorPort, params);
        } else {
            spanCollector = null;
        }
        this.serviceName = serviceName;
        this.isRoot = isRoot;
    }

    public boolean IsRoot() { return isRoot; }

    public ClientTracer getClientTracer()
    {
        return Brave.getClientTracer(spanCollector, traceFilters);
    }

    public ServerTracer getServerTracer()
    {
        return Brave.getServerTracer(spanCollector, traceFilters);
    }

    public EndPointSubmitter getEndPointSubmitter() {
        return Brave.getEndPointSubmitter();
    }

    public AttemptLimiter getInAttemptLimiter() {
        return inAttemptLimiter;
    }

    public AttemptLimiter getOutAttemptLimiter() {
        return outAttemptLimiter;
    }

    public Optional<SpanNameFilter> getSpanNameFilter() {
      return Optional.absent();
    }

    public String getServiceName() {
        return serviceName;
    }

    public void InstallCXFZipkinInterceptors(InterceptorProvider provider){
        if (spanCollector == null)
            return; //No tracing!

        final InZipkinInterceptor inZipkinInterceptor = new InZipkinInterceptor(this);
        provider.getInInterceptors().add(inZipkinInterceptor);

        final OutZipkinInterceptor outZipkinInterceptor = new OutZipkinInterceptor(this);
        provider.getOutInterceptors().add(outZipkinInterceptor);
    }

    //Quick check if initialized without synchronization
    public void RootStartCheck() {
        if (isRoot && !rootInitialized)
            StartRoot(null);
    }

    public void StartRoot(String requestName)
    {
        StartRoot(requestName, "1.1.1.1", 0);
    }

    //Use it only if this is originating root request and no interceptors were fired
    public synchronized void StartRoot(String requestName, String ip, int port)
    {
        if (spanCollector == null)
            return; //No tracing!

        if (!isRoot)
            return;

        if (rootInitialized)
            return;

        rootInitialized = true;

        try {

            ServerSpanThreadBinder threadBinder = Brave.getServerSpanThreadBinder();
            ServerSpan serverSpan = threadBinder.getCurrentServerSpan();
            if (serverSpan.getSpan() != null)
                return;

            String serviceName = Optional.fromNullable(this.serviceName).or("default");

            final EndPointSubmitter endPointSubmitter = Brave.getEndPointSubmitter();
            if (!endPointSubmitter.endPointSubmitted())
                endPointSubmitter.submit(ip, port, serviceName);

            final ServerTracer serverTracer = Brave.getServerTracer(spanCollector, traceFilters);
            serverTracer.setStateUnknown(StringUtils.isBlank(requestName) ? "root" : requestName);
            serverTracer.setServerReceived();

        }
        catch (Exception e) {
            LOG.error("Cannot initialize Zipkin client", e);
        }
    }

    public synchronized void DoneRoot() {
        if (!isRoot || !rootInitialized)
            return;
        final ServerTracer serverTracer = Brave.getServerTracer(spanCollector, traceFilters);
        try {
            serverTracer.setServerSend();
        } finally {
            serverTracer.clearCurrentSpan();
        }

        rootInitialized = false;
    }
}

