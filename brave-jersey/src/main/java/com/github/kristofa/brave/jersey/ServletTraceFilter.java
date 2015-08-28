package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Servlet filter that will extract trace headers from the request and send
 * sr (server received) and ss (server sent) annotations.
 */
@Singleton
public class ServletTraceFilter implements Filter {

    private final ServerRequestInterceptor requestInterceptor;
    private final ServerResponseInterceptor responseInterceptor;
    private final SpanNameProvider spanNameProvider;

    @Inject
    public ServletTraceFilter(
            ServerRequestInterceptor requestInterceptor,
            ServerResponseInterceptor responseInterceptor,
            SpanNameProvider spanNameProvider) {
        this.requestInterceptor = requestInterceptor;
        this.responseInterceptor = responseInterceptor;
        this.spanNameProvider = spanNameProvider;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (traceableRequest(request))
        {
            HttpServerRequest req = new ServletHttpServerRequest((HttpServletRequest) request);
            requestInterceptor.handle(new HttpServerRequestAdapter(req, spanNameProvider));

            final HttpServletResponseDecorator responseDecorator = new HttpServletResponseDecorator((HttpServletResponse) response);
            chain.doFilter(request, responseDecorator);

            final HttpResponse braveResponse = new HttpResponse() {

                @Override
                public int getHttpStatusCode() {
                    return responseDecorator.getStatusCode();
                }
            };
            responseInterceptor.handle(new HttpServerResponseAdapter(braveResponse));
        }
        else
        {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }

    private boolean traceableRequest(ServletRequest request) {
        return request instanceof HttpServerRequest;
    }

    /**
     * Implement decorator to get hold of status code. servlet 2.x api is not so good :(
     */
    static class HttpServletResponseDecorator implements HttpServletResponse {

        private final HttpServletResponse res;
        private int statusCode = 200;

        public HttpServletResponseDecorator(HttpServletResponse res) {
            this.res = res;
        }

        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public void addCookie(Cookie cookie) {
            res.addCookie(cookie);
        }

        @Override
        public boolean containsHeader(String name) {
            return res.containsHeader(name);
        }

        @Override
        public String encodeURL(String url) {
            return res.encodeURL(url);
        }

        @Override
        public String encodeRedirectURL(String url) {
            return res.encodeRedirectURL(url);
        }

        @Override
        public String encodeUrl(String url) {
            return res.encodeUrl(url);
        }

        @Override
        public String encodeRedirectUrl(String url) {
            return res.encodeRedirectUrl(url);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            res.sendError(sc, msg);
        }

        @Override
        public void sendError(int sc) throws IOException {
            res.sendError(sc);
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            res.sendRedirect(location);
        }

        @Override
        public void setDateHeader(String name, long date) {
            res.setDateHeader(name, date);
        }

        @Override
        public void addDateHeader(String name, long date) {
            res.addDateHeader(name, date);
        }

        @Override
        public void setHeader(String name, String value) {
            res.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            res.addHeader(name, value);
        }

        @Override
        public void setIntHeader(String name, int value) {
            res.setIntHeader(name, value);
        }

        @Override
        public void addIntHeader(String name, int value) {
            res.addIntHeader(name, value);
        }

        @Override
        public void setStatus(int sc) {
            statusCode = sc;
            res.setStatus(sc);
        }

        @Override
        public void setStatus(int sc, String sm) {
            statusCode = sc;
            res.setStatus(sc, sm);
        }

        @Override
        public String getCharacterEncoding() {
            return res.getCharacterEncoding();
        }

        @Override
        public String getContentType() {
            return res.getContentType();
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return res.getOutputStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            return res.getWriter();
        }

        @Override
        public void setCharacterEncoding(String charset) {
            res.setCharacterEncoding(charset);
        }

        @Override
        public void setContentLength(int len) {
            res.setContentLength(len);
        }

        @Override
        public void setContentType(String type) {
            res.setContentType(type);
        }

        @Override
        public void setBufferSize(int size) {
            res.setBufferSize(size);
        }

        @Override
        public int getBufferSize() {
            return res.getBufferSize();
        }

        @Override
        public void flushBuffer() throws IOException {
            res.flushBuffer();
        }

        @Override
        public void resetBuffer() {
            res.resetBuffer();
        }

        @Override
        public boolean isCommitted() {
            return res.isCommitted();
        }

        @Override
        public void reset() {
            res.reset();
        }

        @Override
        public void setLocale(Locale loc) {
            res.setLocale(loc);
        }

        @Override
        public Locale getLocale() {
            return res.getLocale();
        }
    }

}
