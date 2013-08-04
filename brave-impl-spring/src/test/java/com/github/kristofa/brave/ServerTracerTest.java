package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ServerTracerTest {

    @Test
    public void testServerTracer() {
        final AnnotationConfigApplicationContext ctx =
            new AnnotationConfigApplicationContext(SpanCollectorMockConfig.class, ServerTracerConfig.class);
        try {
            final ServerTracer bean1 = ctx.getBean(ServerTracer.class);
            assertNotNull(bean1);
            final ServerTracer bean2 = ctx.getBean(ServerTracer.class);
            assertSame("Expect singleton.", bean1, bean2);

        } finally {
            ctx.close();
        }
    }

}
