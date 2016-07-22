package com.github.kristofa.brave.p6spy;

import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.spy.P6Factory;
import com.p6spy.engine.spy.P6LoadableOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;

public final class P6BraveFactory implements P6Factory {

    private P6BraveOptions options;

    @Override
    public P6LoadableOptions getOptions(P6OptionsRepository optionsRepository) {
        this.options = new P6BraveOptions(optionsRepository);
        return options;
    }

    @Override
    public JdbcEventListener getJdbcEventListener() {
        return new BraveP6SpyListener(options);
    }

}
