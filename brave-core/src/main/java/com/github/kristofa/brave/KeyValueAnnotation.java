package com.github.kristofa.brave;


import java.util.Objects;

public class KeyValueAnnotation {

    private final String key;
    private final String value;

    public KeyValueAnnotation(String key, String value) {
        this.key = Objects.requireNonNull(key, "Key should not be null.");
        this.value = Objects.requireNonNull(value, "Value should not be null.");
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj instanceof KeyValueAnnotation) {
            KeyValueAnnotation other = (KeyValueAnnotation) obj;
            return Objects.equals(key, other.key) && Objects.equals(value, other.value);
        }
        return false;

    }
}
