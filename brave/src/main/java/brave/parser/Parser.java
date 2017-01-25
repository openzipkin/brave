package brave.parser;

import brave.internal.Nullable;

/**
 * Parses a small amount of information from a source. Usually used to get span names or tag values.
 *
 * @param <S> source of data, often an http or rpc message
 * @param <V> value type, often string
 */
public interface Parser<S, V> {
  /** Returns null if absent or unreadable */
  @Nullable V parse(S source);
}
