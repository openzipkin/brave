package com.github.kristofa.brave;

import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import javax.annotation.Nullable;

/**
 * @deprecated this type was only used for {@link AnnotationSubmitter#create(SpanAndEndpoint,
 * AnnotationSubmitter.Clock)}, which is deprecated.
 */
@Deprecated
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
     * Indicates the network context of the local service being traced.
     *
     * @return Endpoint to add to annotations. Cannot be <code>null</code>.
     */
    Endpoint endpoint();

    /**
     * @deprecated this type was only used for {@link AnnotationSubmitter#create(SpanAndEndpoint,
     * AnnotationSubmitter.Clock)}, which is deprecated.
     */
    @Deprecated
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
            return state().endpoint();
        }
    }

    /**
     * @deprecated this type was only used for {@link AnnotationSubmitter#create(SpanAndEndpoint,
     * AnnotationSubmitter.Clock)}, which is deprecated.
     */
    @Deprecated
    @AutoValue
    abstract class ClientSpanAndEndpoint implements SpanAndEndpoint {
        abstract ServerClientAndLocalSpanState state();

        static ClientSpanAndEndpoint create(ServerClientAndLocalSpanState state) {
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
            return state().endpoint();
        }
    }

    /**
     * @deprecated this type was only used for {@link AnnotationSubmitter#create(SpanAndEndpoint,
     * AnnotationSubmitter.Clock)}, which is deprecated.
     */
    @Deprecated
    @AutoValue
    abstract class LocalSpanAndEndpoint implements SpanAndEndpoint {
        abstract ServerClientAndLocalSpanState state();

        static LocalSpanAndEndpoint create(ServerClientAndLocalSpanState state) {
            return new AutoValue_SpanAndEndpoint_LocalSpanAndEndpoint(state);
        }

        @Override
        public Span span() {
            return state().getCurrentLocalSpan();
        }

        @Override
        public Endpoint endpoint() {
            return state().endpoint();
        }
    }
}
