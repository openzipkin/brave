package com.github.kristofa.brave;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class KeyValueAnnotation {

  public static KeyValueAnnotation create(String key, String value) {
    return new AutoValue_KeyValueAnnotation(key, value);
  }

  public abstract String getKey();

  public abstract String getValue();

  KeyValueAnnotation() {
  }
}
