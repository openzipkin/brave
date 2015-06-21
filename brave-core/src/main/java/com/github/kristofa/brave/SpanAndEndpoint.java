package com.github.kristofa.brave;

import com.google.auto.value.AutoValue;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

import javax.annotation.Nullable;

public interface SpanAndEndpoint {

    /**
     * Gets the span to which to add annotations.
     *
     * @return Span to which to add annotations. Can be <code>null</code>. In that case the different submit methods will not
     *         do anything.
     */
    @Nullable
    Span span();

    /**
     * Gets the Endpoint for the annotations. (Server Endpoint or Client Endpoint depending on the context)
     *
     * @return Endpoint for the annotations. Can be <code>null</code>.
     */
    @Nullable
    Endpoint endpoint();

    /**
     * Span and endpoint never change reference.
     */
    @AutoValue
    abstract class StaticSpanAndEndpoint implements SpanAndEndpoint {
      static StaticSpanAndEndpoint create(@Nullable Span span, @Nullable Endpoint endpoint) {
        return new AutoValue_SpanAndEndpoint_StaticSpanAndEndpoint(span, endpoint);
      }
    }

    @AutoValue
    abstract class ServerSpanAndEndpoint implements SpanAndEndpoint {
        abstract ServerSpanState state();

        static ServerSpanAndEndpoint create(ServerSpanState state) {
            return new AutoValue_SpanAndEndpoint_ServerSpanAndEndpoint(state);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Span span() {
            return state().getCurrentServerSpan().getSpan();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Endpoint endpoint() {
            return state().getServerEndpoint();
        }
    }

    @AutoValue
    abstract class ClientSpanAndEndpoint implements SpanAndEndpoint {
        abstract ServerAndClientSpanState state();

        static ClientSpanAndEndpoint create(ServerAndClientSpanState state) {
            return new AutoValue_SpanAndEndpoint_ClientSpanAndEndpoint(state);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Span span() {
            return state().getCurrentClientSpan();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Endpoint endpoint() {
            return state().getClientEndpoint();
        }
    }
}
