package brave.internal.correlation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class RealCorrelationFields extends CorrelationFields {
  public static CorrelationFields create() {
    return new RealCorrelationFields();
  }

  // TODO: consider if we shouldn't use array offset storage to reduce memory and cloning cost
  final LinkedHashMap<String, String> storage = new LinkedHashMap<>();

  @Override public boolean isNoop() {
    return false;
  }

  @Override public void set(String name, String value) {
    if (!checkValid(name, "name")) return;
    if (!checkValid(value, "value")) return;
    synchronized (storage) {
      storage.put(name, value);
    }
  }

  @Override public void setAll(CorrelationFields other) {
    if (other.isEmpty()) return;
    // bug if cast fail as this is a package-sealed type and noop is always empty
    RealCorrelationFields realOther = (RealCorrelationFields) other;
    synchronized (storage) {
      storage.putAll(realOther.storage);
    }
  }

  @Override public String get(String name) {
    if (!checkValid(name, "name")) return null;
    final String result;
    synchronized (storage) {
      result = storage.get(name);
    }
    return result;
  }

  @Override public boolean isEmpty() {
    final boolean result;
    synchronized (storage) {
      result = storage.isEmpty();
    }
    return result;
  }

  @Override public void forEach(Consumer consumer) {
    final Set<Map.Entry<String, String>> entrySet;
    synchronized (storage) {
      entrySet = new LinkedHashSet<>(storage.entrySet());
    }
    for (Map.Entry<String, String> entry : entrySet) {
      consumer.accept(entry.getKey(), entry.getValue());
    }
  }

  @Override public CorrelationFields clone() {
    RealCorrelationFields result = new RealCorrelationFields();
    synchronized (storage) {
      result.storage.putAll(storage);
    }
    return result;
  }

  /** borrowed from census in order to be defensive and error early while testing this feature */
  static boolean checkValid(String input, String type) {
    if (input == null) return log(type + " == null");
    int size = input.length();
    if (size == 0) return log(type + " was empty");
    if (size > 255) return log(type + " is longer than 255 characters");
    for (int i = 0; i < size; i++) {
      char c = input.charAt(i);
      if (c >= ' ' && c <= '~') continue;
      return log(type + " " + input + " contains an unprintable character");
    }
    return true;
  }

  private static boolean log(String message) {
    return false; // TODO: log
  }

  @Override public String toString() {
    return "RealCorrelationFields" + storage;
  }

  RealCorrelationFields() {
  }
}
