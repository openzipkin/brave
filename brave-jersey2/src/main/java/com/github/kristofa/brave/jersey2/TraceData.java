/*
 *
 *  * Copyright (C) 2014 by Teradata Corporation.
 *  * All Rights Reserved.
 *  * TERADATA CONFIDENTIAL AND TRADE SECRET
 *
 */

package com.github.kristofa.brave.jersey2;

/**
 * Created by ct186007 on 9/29/14.
 */
public class TraceData {

    private Long traceId;
    private Long spanId;
    private Long parentSpanId;
    private Boolean shouldBeSampled;
    private String spanName;

    public void setTraceId(final Long traceId) {
        if (traceId != null) {
            this.traceId = traceId;
        }
    }

    public void setSpanId(final Long spanId) {
        if (spanId != null) {
            this.spanId = spanId;
        }
    }

    public void setParentSpanId(final Long parentSpanId) {
        this.parentSpanId = parentSpanId;
    }

    public void setShouldBeSampled(final Boolean shouldBeSampled) {
        this.shouldBeSampled = shouldBeSampled;
    }

    public void setSpanName(final String spanName) {
        this.spanName = spanName;
    }

    public Long getTraceId() {
        return traceId;
    }

    public Long getSpanId() {
        return spanId;
    }

    public Long getParentSpanId() {
        return parentSpanId;
    }

    public Boolean shouldBeTraced() {
        return shouldBeSampled;
    }

    public String getSpanName() {
        return spanName;
    }

}
