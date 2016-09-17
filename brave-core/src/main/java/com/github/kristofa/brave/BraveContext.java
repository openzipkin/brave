package com.github.kristofa.brave;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The {@link BraveContext} is a registry of all {@link Brave} instances that have been created.
 */
public class BraveContext {

    /**
     * Application's should not be creating large numbers of {@link Brave} instances, so it is preferable
     * to copy this every time as opposed to taking a read/write lock.
     */
    private static final CopyOnWriteArrayList<Brave> instances = new CopyOnWriteArrayList<Brave>();

    private static volatile boolean initialized;
    private static volatile Brave firstInstance;

    /**
     * Register instances of {@link Brave} with the context.
     *
     * @param brave {@link Brave} instance
     */
    static void register(Brave brave) {
        if (!initialized) {
            synchronized (BraveContext.class) {
                if (!initialized) {
                    firstInstance = brave;
                    initialized = true;
                }
            }
        }

        instances.add(brave);
    }

    /**
     * Visible for testing.
     */
    static void clear() {
        synchronized (BraveContext.class) {
            initialized = false;
            firstInstance = null;
            instances.clear();
        }
    }

    /**
     * Get the first instance registered with the context or null if none have been registered.
     *
     * @return the {@link Brave} instance
     */
    public static Brave get() {
        return firstInstance;
    }

    /**
     * Get all {@link Brave} instances that have been created.
     *
     * @return a list of {@link Brave} instances
     */
    public static List<Brave> getInstances() {
        return Collections.unmodifiableList(instances);
    }

}
