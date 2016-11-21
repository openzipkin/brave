package com.github.kristofa.brave.internal;

import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.TagExtractor.ValueParser;
import com.github.kristofa.brave.TagExtractor.ValueParserFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

public final class TagExtractorBuilder implements TagExtractor.Config<TagExtractorBuilder> {
  public static TagExtractorBuilder create() {
    return new TagExtractorBuilder();
  }

  final List<ValueParserFactory> valueParserFactories = new ArrayList<ValueParserFactory>();
  final Set<String> keys = new LinkedHashSet<String>();

  @Override
  public TagExtractorBuilder addKey(String key) {
    keys.add(checkNotNull(key, "key"));
    return this;
  }

  @Override
  public TagExtractorBuilder addValueParserFactory(ValueParserFactory factory) {
    valueParserFactories.add(0, checkNotNull(factory, "factory"));
    return this;
  }

  public <T> TagExtractor<T> build(Class<? extends T> type) {
    Map<String, ValueParser<T>> result = new LinkedHashMap<String, ValueParser<T>>();
    for (String key : keys) {
      for (ValueParserFactory factory : valueParserFactories) {
        ValueParser valueParser = factory.create(type, key);
        if (valueParser != null) {
          result.put(key, valueParser);
        }
      }
    }
    if (result.isEmpty()) {
      return (TagExtractor<T>) EMPTY;
    }
    if (result.size() == 1) {
      Map.Entry<String, ValueParser<T>> only = result.entrySet().iterator().next();
      return new SingleTagExtractor<T>(only.getKey(), only.getValue());
    }
    return new MultipleTagExtractor<T>(result);
  }

  TagExtractorBuilder() {
  }

  private static final TagExtractor<Object> EMPTY = new TagExtractor() {
    @Override public Set<KeyValueAnnotation> extractTags(Object input) {
      return Collections.emptySet();
    }
  };

  static final class SingleTagExtractor<T> implements TagExtractor<T> {
    final String key;
    final ValueParser<T> valueParser;

    SingleTagExtractor(String key, ValueParser<T> valueParser) {
      this.valueParser = valueParser;
      this.key = key;
    }

    @Override public Set<KeyValueAnnotation> extractTags(T input) {
      String value = valueParser.parse(input);
      if (value != null) {
        return Collections.<KeyValueAnnotation>singleton(KeyValueAnnotation.create(key, value));
      } else {
        return Collections.<KeyValueAnnotation>emptySet();
      }
    }
  }

  static final class MultipleTagExtractor<T> implements TagExtractor<T> {
    final String[] keys;
    final ValueParser<T>[] valueParsers;

    MultipleTagExtractor(Map<String, ValueParser<T>> map) {
      this.keys = new String[map.size()];
      this.valueParsers = new ValueParser[map.size()];
      int i = 0;
      for (Map.Entry<String, ValueParser<T>> entry : map.entrySet()) {
        keys[i] = entry.getKey();
        valueParsers[i++] = entry.getValue();
      }
    }

    @Override public Set<KeyValueAnnotation> extractTags(T input) {
      Set<KeyValueAnnotation> result = new LinkedHashSet<KeyValueAnnotation>();
      for (int i = 0; i < keys.length; i++) {
        String value = valueParsers[i].parse(input);
        if (value != null) {
          result.add(KeyValueAnnotation.create(keys[i], value));
        }
      }
      return result;
    }
  }
}