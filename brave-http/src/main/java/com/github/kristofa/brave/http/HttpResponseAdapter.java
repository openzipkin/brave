package com.github.kristofa.brave.http;

import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.TagExtractor.ValueParserFactory;
import com.github.kristofa.brave.internal.TagExtractorBuilder;
import java.util.Collection;
import zipkin.TraceKeys;

class HttpResponseAdapter<R extends HttpResponse> {
  private final R response;
  private final TagExtractor<R> tagExtractor;

  static abstract class FactoryBuilder<B extends FactoryBuilder<B>>
      implements TagExtractor.Config<B> {
    private final TagExtractorBuilder tagExtractorBuilder = TagExtractorBuilder.create()
        .addKey(TraceKeys.HTTP_STATUS_CODE)
        .addValueParserFactory(new HttpResponseValueParserFactory());

    @Override public B addKey(String key) {
      tagExtractorBuilder.addKey(key);
      return (B) this;
    }

    @Override public B addValueParserFactory(ValueParserFactory factory) {
      tagExtractorBuilder.addValueParserFactory(factory);
      return (B) this;
    }

    FactoryBuilder() { // intentionally hidden
    }
  }

  static abstract class Factory<R extends HttpResponse, A> {
    final TagExtractor<R> tagExtractor;

    Factory(FactoryBuilder<?> builder, Class<? extends R> responseType) {
      // java 6 generics cannot figure this out
      this.tagExtractor = (TagExtractor) builder.tagExtractorBuilder.build(responseType);
    }

    abstract A create(R request);
  }

  HttpResponseAdapter(Factory<R, ?> factory, R response) { // intentionally hidden
    this.tagExtractor = factory.tagExtractor;
    this.response = response;
  }

  public Collection<KeyValueAnnotation> responseAnnotations() {
    return tagExtractor.extractTags(response);
  }
}
