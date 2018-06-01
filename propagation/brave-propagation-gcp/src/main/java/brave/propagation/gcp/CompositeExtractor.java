package brave.propagation.gcp;

import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;

public class CompositeExtractor<C> implements TraceContext.Extractor<C> {

	private TraceContext.Extractor<C>[] extractors;

	public CompositeExtractor(TraceContext.Extractor<C>... extractors) {
		this.extractors = extractors;
	}

	@Override
	public TraceContextOrSamplingFlags extract(C carrier) {
		TraceContextOrSamplingFlags context = TraceContextOrSamplingFlags.EMPTY;

		int currentExtractor = 0;
		while (context == TraceContextOrSamplingFlags.EMPTY
				&& currentExtractor < extractors.length) {
			context = extractors[currentExtractor++].extract(carrier);
		}

		return context;
	}
}
