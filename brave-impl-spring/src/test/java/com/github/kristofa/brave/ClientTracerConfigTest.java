package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ClientTracerConfigTest {

    @Test
    public void testClientTracer() {
        final AnnotationConfigApplicationContext ctx =
            new AnnotationConfigApplicationContext(TraceFiltersMockConfig.class, SpanCollectorMockConfig.class,
                ClientTracerConfig.class);
        try {
            final ClientTracer bean1 = ctx.getBean(ClientTracer.class);
            assertNotNull(bean1);
            final ClientTracer bean2 = ctx.getBean(ClientTracer.class);
            assertSame("Expect singleton.", bean1, bean2);

        } finally {
            ctx.close();
        }
    }

}
