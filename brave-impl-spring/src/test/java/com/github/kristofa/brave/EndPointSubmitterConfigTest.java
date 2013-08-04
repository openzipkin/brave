package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class EndPointSubmitterConfigTest {

    @Test
    public void test() {
        final AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(EndPointSubmitterConfig.class);
        try {
            final EndPointSubmitter bean1 = ctx.getBean(EndPointSubmitter.class);
            assertNotNull(bean1);
            final EndPointSubmitter bean2 = ctx.getBean(EndPointSubmitter.class);
            assertSame("Expect singleton.", bean1, bean2);

        } finally {
            ctx.close();
        }
    }

}
