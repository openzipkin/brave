package com.github.kristofa.brave.spring;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.github.kristofa.brave.ServerAdapterInterceptor;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseAdapter;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.HttpServerRequest;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;

public class ServletHandlerInterceptor extends HandlerInterceptorAdapter {

    static final String HTTP_SERVER_SPAN_ATTRIBUTE = ServletHandlerInterceptor.class.getName() + ".server-span";

    private final ServerRequestInterceptor requestInterceptor;
    private final SpanNameProvider spanNameProvider;
    private final ServerResponseInterceptor responseInterceptor;
    private final ServerSpanThreadBinder serverThreadBinder;
    
    private List<ServerAdapterInterceptor> adapterInterceptors = new ArrayList<ServerAdapterInterceptor>();

    @Autowired
    public ServletHandlerInterceptor(ServerRequestInterceptor requestInterceptor, ServerResponseInterceptor responseInterceptor, SpanNameProvider spanNameProvider, final ServerSpanThreadBinder serverThreadBinder) {
        this.requestInterceptor = requestInterceptor;
        this.spanNameProvider = spanNameProvider;
        this.responseInterceptor = responseInterceptor;
        this.serverThreadBinder = serverThreadBinder;
    }
    
    public void addAdapterInterceptor(ServerAdapterInterceptor adapterInterceptor){
        adapterInterceptors.add(adapterInterceptor);
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        requestInterceptor.handle(interceptRequestAdapter(new HttpServerRequestAdapter(newRequest(request), spanNameProvider)));
        return true;
    }

    @Override
    public void afterConcurrentHandlingStarted(final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        request.setAttribute(HTTP_SERVER_SPAN_ATTRIBUTE, serverThreadBinder.getCurrentServerSpan());
        serverThreadBinder.setCurrentSpan(null);
    }

    @Override
    public void afterCompletion(final HttpServletRequest request, final HttpServletResponse response, final Object handler, final Exception ex) {
        final ServerSpan span = (ServerSpan) request.getAttribute(HTTP_SERVER_SPAN_ATTRIBUTE);
        if (span != null) {
            serverThreadBinder.setCurrentSpan(span);
        }
        responseInterceptor.handle(interceptResponseAdapter(newResponseAdapter(newResponse(response))));
    }
    

    protected ServerRequestAdapter newRequestAdapter(HttpServerRequest request, SpanNameProvider spanNameProvider) {
		return new HttpServerRequestAdapter(request, spanNameProvider);
	}
    

    protected ServerResponseAdapter newResponseAdapter(HttpResponse response) {
		return new HttpServerResponseAdapter(response);
	}

    protected HttpServerRequest newRequest(final HttpServletRequest request) {
    	return new HttpServerRequest() {
            @Override
            public String getHttpHeaderValue(String headerName) {
                return request.getHeader(headerName);
            }

            @Override
            public URI getUri() {
                try {
                    return new URI(request.getRequestURI());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public String getHttpMethod() {
                return request.getMethod();
            }
        };
    }
    
	protected HttpResponse newResponse(final HttpServletResponse response) {
    	return new HttpResponse() {
            @Override
            public int getHttpStatusCode() {
                return response.getStatus();
            }
        };
	}

	protected ServerRequestAdapter interceptRequestAdapter(ServerRequestAdapter adapter) {
        ServerRequestAdapter interceptedAdapter = adapter;
        for(ServerAdapterInterceptor adapterInterceptor : adapterInterceptors){
            interceptedAdapter = adapterInterceptor.interceptRequestAdapter(interceptedAdapter);
        }
        return interceptedAdapter;
    }
    
    protected ServerResponseAdapter interceptResponseAdapter(ServerResponseAdapter adapter) {
        ServerResponseAdapter interceptedAdapter = adapter;
        for(ServerAdapterInterceptor adapterInterceptor : adapterInterceptors){
            interceptedAdapter = adapterInterceptor.interceptResponseAdapter(interceptedAdapter);
        }
        return interceptedAdapter;
    }
}
