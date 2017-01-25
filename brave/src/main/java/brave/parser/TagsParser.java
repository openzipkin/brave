package brave.parser;

import brave.Span;

/**
 * Parses binary annotation/tags based for a given input type. Implemented as a list of key and
 * value parsers
 */
public interface TagsParser<S> {

  /**
   * Any non-null values parsed from the configured keys are returned. Does not return null.
   */
  void addTagsToSpan(S input, Span span);
}