# brave-jersey
This contains a Jersey 1.x `ClientFilter` that intercepts api requests,
propagating and reporting spans with `cs` and `cr` annotations.

## Configuration

Configuration in Jersey 1.x depends on the container used. In all cases,
you will need configured instances of `Brave` and `SpanNameProvider`.

```
webResource.addFilter(JerseyClientTraceFilter.create(brave));
```

### JSR330 (javax.inject) Configuration

You can also setup Jersey 1.x tracing in a JSR330 framework like
[guice-servlet](https://github.com/google/guice/wiki/ServletModule).
Make sure you have bindings for `Brave` and `SpanNameProvider` before
proceeding further.

Client tracing is not much different, except you can inject
`JerseyClientTraceFilter` as opposed to using a builder.

While this module doesn't include server code, it does include a
convenience type `ServletTraceFilter`, for use in JSR330 frameworks. You
can you can use this instead of [explicit instantiation](../brave-web-servlet-filter/README.md).
