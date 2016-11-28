package com.github.kristofa.brave.http;

import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.internal.TagExtractorBuilder;
import java.util.Collection;
import zipkin.TraceKeys;

class HttpRequestAdapter<R extends HttpRequest> {

  final R request;
  private final SpanNameProvider spanNameProvider;
  private final TagExtractor<R> tagExtractor;

  static abstract class FactoryBuilder<B extends FactoryBuilder<B>>
      implements TagExtractor.Config<B> {
    private final TagExtractorBuilder tagExtractorBuilder = TagExtractorBuilder.create()
        .addKey(TraceKeys.HTTP_URL)
        .addValueParserFactory(new HttpRequestValueParserFactory());
    private SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();

    public B spanNameProvider(SpanNameProvider spanNameProvider) {
      this.spanNameProvider = spanNameProvider;
      return (B) this;
    }

    @Override public B addKey(String key) {
      tagExtractorBuilder.addKey(key);
      return (B) this;
    }

    @Override public B addValueParserFactory(TagExtractor.ValueParserFactory factory) {
      tagExtractorBuilder.addValueParserFactory(factory);
      return (B) this;
    }

    FactoryBuilder() { // intentionally hidden
    }
  }

  static abstract class Factory<R extends HttpRequest, A> {
    final SpanNameProvider spanNameProvider;
    final TagExtractor<R> tagExtractor;

    Factory(FactoryBuilder<?> builder, Class<? extends R> requestType) {
      this.spanNameProvider = builder.spanNameProvider;
      // java 6 generics cannot figure this out
      this.tagExtractor = (TagExtractor) builder.tagExtractorBuilder.build(requestType);
    }

    abstract A create(R request);
  }

  HttpRequestAdapter(Factory<R, ?> builder, R request) { // intentionally hidden
    this.spanNameProvider = builder.spanNameProvider;
    this.tagExtractor = builder.tagExtractor;
    this.request = request;
  }

  public String getSpanName() {
    return spanNameProvider.spanName(request);
  }

  public Collection<KeyValueAnnotation> requestAnnotations() {
    return tagExtractor.extractTags(request);
  }
}
