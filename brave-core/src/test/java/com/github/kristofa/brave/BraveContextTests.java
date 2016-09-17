package com.github.kristofa.brave;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

public class BraveContextTests {

    @Before
    public void before() {
        BraveContext.clear();
    }

    @Test
    public void noInstanceIsReturnedWhenNoneAreRegistered() {
        assertThat(BraveContext.get()).isNull();
    }

    @Test
    public void braveInstancesAreRegisteredWithContext() {
        Brave brave = new Brave.Builder()
            .spanCollector(new EmptySpanCollector())
            .traceSampler(Sampler.NEVER_SAMPLE)
            .build();

        assertThat(BraveContext.get()).isEqualTo(brave);

        Brave brave2 = new Brave.Builder()
            .spanCollector(new EmptySpanCollector())
            .traceSampler(Sampler.ALWAYS_SAMPLE)
            .build();

        assertThat(BraveContext.get()).isEqualTo(brave);
        assertThat(BraveContext.getInstances()).contains(brave, brave2);
    }
}
