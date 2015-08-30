package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class BraveApiConfigTest {

    private AnnotationConfigApplicationContext ctx;

    @Before
    public void setup() {
        ctx =
                new AnnotationConfigApplicationContext(BraveConfig.class,
                        BraveApiConfig.class);
    }

    @After
    public void after() {
        ctx.close();
    }

    @Test
    public void testClientTracer() {
        final ClientTracer bean1 = ctx.getBean(ClientTracer.class);
        assertNotNull(bean1);
        final ClientTracer bean2 = ctx.getBean(ClientTracer.class);
        assertSame("Expect singleton.", bean1, bean2);
    }

    @Test
    public void testServerTracer() {
        final ServerTracer bean1 = ctx.getBean(ServerTracer.class);
        assertNotNull(bean1);
        final ServerTracer bean2 = ctx.getBean(ServerTracer.class);
        assertSame("Expect singleton.", bean1, bean2);
    }

    @Test
    public void testClientRequestInterceptor() {
        final ClientRequestInterceptor bean1 = ctx.getBean(ClientRequestInterceptor.class);
        assertNotNull(bean1);
        final ClientRequestInterceptor bean2 = ctx.getBean(ClientRequestInterceptor.class);
        assertSame("Expect singleton.", bean1, bean2);
    }

    @Test
    public void testClientResponseInterceptor() {
        final ClientResponseInterceptor bean1 = ctx.getBean(ClientResponseInterceptor.class);
        assertNotNull(bean1);
        final ClientResponseInterceptor bean2 = ctx.getBean(ClientResponseInterceptor.class);
        assertSame("Expect singleton.", bean1, bean2);
    }

    @Test
    public void testServerRequestInterceptor() {
        final ServerRequestInterceptor bean1 = ctx.getBean(ServerRequestInterceptor.class);
        assertNotNull(bean1);
        final ServerRequestInterceptor bean2 = ctx.getBean(ServerRequestInterceptor.class);
        assertSame("Expect singleton.", bean1, bean2);
    }

    @Test
    public void testServerResponseInterceptor() {
        final ServerResponseInterceptor bean1 = ctx.getBean(ServerResponseInterceptor.class);
        assertNotNull(bean1);
        final ServerResponseInterceptor bean2 = ctx.getBean(ServerResponseInterceptor.class);
        assertSame("Expect singleton.", bean1, bean2);
    }

    @Test
    public void testAnnotationSubmitter() {
        final AnnotationSubmitter bean1 = ctx.getBean("serverSpanAnnotationSubmitter", AnnotationSubmitter.class);
        assertNotNull(bean1);
        final AnnotationSubmitter bean2 = ctx.getBean("serverSpanAnnotationSubmitter", AnnotationSubmitter.class);
        assertSame("Expect singleton.", bean1, bean2);
    }

    @Test
    public void testServerSpanThreadBinder() {
        final ServerSpanThreadBinder bean1 = ctx.getBean(ServerSpanThreadBinder.class);
        assertNotNull(bean1);
        final ServerSpanThreadBinder bean2 = ctx.getBean(ServerSpanThreadBinder.class);
        assertSame("Expect singleton.", bean1, bean2);
    }

}
