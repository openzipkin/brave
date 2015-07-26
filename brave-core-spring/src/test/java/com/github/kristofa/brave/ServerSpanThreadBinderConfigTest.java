package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ServerSpanThreadBinderConfigTest {

    @Test
    public void testThreadBinder() {
        final AnnotationConfigApplicationContext ctx =
            new AnnotationConfigApplicationContext(ServerSpanThreadBinderConfig.class);
        try {
            final ServerSpanThreadBinderConfig bean1 = ctx.getBean(ServerSpanThreadBinderConfig.class);
            assertNotNull(bean1);
            final ServerSpanThreadBinderConfig bean2 = ctx.getBean(ServerSpanThreadBinderConfig.class);
            assertSame("Expect singleton.", bean1, bean2);

        } finally {
            ctx.close();
        }
    }

}
