package brave.propagation.gcp;

import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;

import java.util.Collections;
import java.util.List;

public final class StackdriverTracePropagation<K> implements Propagation<K> {

	public static final Propagation.Factory FACTORY = new Propagation.Factory() {
		@Override public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
			return new StackdriverTracePropagation<>(keyFactory);
		}

		@Override public boolean supportsJoin() {
			return true;
		}

		@Override public String toString() {
			return "StackdriverTracePropagationFactory";
		}
	};

	/**
	 * 128 or 64-bit trace ID lower-hex encoded into 32 or 16 characters (required)
	 */
	static final String TRACE_ID_NAME = "x-cloud-trace-context";

	private Propagation<K> b3Propagation;
	final K traceIdKey;
	final List<K> fields;

	StackdriverTracePropagation(KeyFactory<K> keyFactory) {
		this.traceIdKey = keyFactory.create(TRACE_ID_NAME);
		this.fields = Collections.unmodifiableList(Collections.singletonList(traceIdKey));
		this.b3Propagation = B3Propagation.FACTORY.create(keyFactory);
	}

	@Override public List<K> keys() {
		return fields;
	}

	@Override public <C> TraceContext.Injector<C> injector(Setter<C, K> setter) {
		return b3Propagation.injector(setter);
	}

	@Override public <C> TraceContext.Extractor<C> extractor(Getter<C, K> getter) {
		if (getter == null) throw new NullPointerException("getter == null");
		return new CompositeExtractor<>(
				new XCloudTraceContextExtractor<>(this, getter),
				b3Propagation.extractor(getter));
	}

	static final class XCloudTraceContextExtractor<C, K> implements TraceContext.Extractor<C> {
		final StackdriverTracePropagation<K> propagation;
		final Getter<C, K> getter;

		XCloudTraceContextExtractor(StackdriverTracePropagation<K> propagation,
									Getter<C, K> getter) {
			this.propagation = propagation;
			this.getter = getter;
		}

		@Override public TraceContextOrSamplingFlags extract(C carrier) {
			if (carrier == null) throw new NullPointerException("carrier == null");

			String traceIdString = getter.get(carrier, propagation.traceIdKey);

			// Try to parse the trace IDs into the context
			TraceContext.Builder result = TraceContext.newBuilder();
			if (result.parseTraceId(traceIdString, propagation.traceIdKey)) {
				return TraceContextOrSamplingFlags.create(result.build());
			}
			return TraceContextOrSamplingFlags.EMPTY;
		}
	}
}

