package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class EndpointSubmitterConfigTest {

    @Test
    public void test() {
        final AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(EndpointSubmitterConfig.class);
        try {
            final EndpointSubmitter bean1 = ctx.getBean(EndpointSubmitter.class);
            assertNotNull(bean1);
            final EndpointSubmitter bean2 = ctx.getBean(EndpointSubmitter.class);
            assertSame("Expect singleton.", bean1, bean2);

        } finally {
            ctx.close();
        }
    }

}
