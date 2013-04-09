package com.github.kristofa.brave.resteasy;


class TraceData {

    private Long traceId;
    private Long spanId;
    private Long parentSpanId;
    private Boolean shouldBeSampled;

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

    public Long getTraceId() {
        return traceId;
    }

    public Long getSpanId() {
        return spanId;
    }

    public Long getParentSpanId() {
        return parentSpanId;
    }

    public boolean shouldBeTraced() {
        if (shouldBeSampled == null || Boolean.TRUE.equals(shouldBeSampled)) {
            return true;
        }
        return false;
    }

}
