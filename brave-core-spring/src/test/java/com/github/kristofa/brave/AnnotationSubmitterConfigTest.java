package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class AnnotationSubmitterConfigTest {

    @Test
    public void testAnnotationSubmitter() {
        final AnnotationConfigApplicationContext ctx =
            new AnnotationConfigApplicationContext(AnnotationSubmitterConfig.class);
        try {
            final AnnotationSubmitter bean1 = ctx.getBean(AnnotationSubmitter.class);
            assertNotNull(bean1);
            final AnnotationSubmitter bean2 = ctx.getBean(AnnotationSubmitter.class);
            assertSame("Expect singleton.", bean1, bean2);

        } finally {
            ctx.close();
        }
    }

}
