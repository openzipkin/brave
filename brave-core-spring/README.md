# brave-core-spring #


The `brave-core-spring` module has Spring dependency injection configuration support for `brave-core` 

It does not use XML configuration but Java based container configuration using annotations.

If you include this module on your classpath you can add the configurations to your Spring
context by including `com.github.kristofa.brave.BraveApiConfig`.

Spring is added as a Maven dependency with 'provided' scope so you have to include Spring as compile scope
dependency to you own application. This gives you the freedom to choose the Spring version of 
your choice (the config classes are tested with Spring 4.1.6.RELEASE).

There is no configuration provided for the `Brave` instance (see `brave-core`). You have to provide a Spring
config class for this yourself as you probably want to decide anyway which `SpanCollector` and which `TraceFilters`
to use.

