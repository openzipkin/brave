/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.kristofa.brave.slf4j;

import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.internal.Util;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;

/**
 * {@link SpanCollector} implementation that logs through SLF4J at INFO level.
 */
public final class SynchronousSlf4jLoggingSpanCollector implements SpanCollector {

    private final Logger logger;
    private final Set<BinaryAnnotation> annotations = new LinkedHashSet<>();

    public SynchronousSlf4jLoggingSpanCollector(String loggerName) {
        this(LoggerFactory.getLogger(Util.checkNotBlank(loggerName, "loggerName must not be blank or null")));
    }

    public SynchronousSlf4jLoggingSpanCollector(Logger logger) {
        this.logger = Util.checkNotNull(logger, "logger must not be null");
    }

    @Override
    public void collect(Span span) {
        Util.checkNotNull(span, "span must not be null");
        if (getLogger().isInfoEnabled()) {
            for (BinaryAnnotation ba : getAnnotations()) {
                span.addToBinary_annotations(ba);
            }

            getLogger().info(span.toString());
        }
    }

    @Override
    public void addDefaultAnnotation(String key, String value) {
        getAnnotations().add(BinaryAnnotation.create(key, value, null));
    }

    public Logger getLogger() {
        return logger;
    }

    public Set<BinaryAnnotation> getAnnotations() {
        return annotations;
    }
}

