# brave-instrumentation-p6spy
This includes a tracing event listener for [P6Spy](https://github.com/p6spy/p6spy) (a proxy for calls to your JDBC driver).
It reports to Zipkin how long each statement takes, along with relevant tags like the query.


P6Spy requires a `spy.properties` in your application classpath
(ex `src/main/resources`). `brave.p6spy.TracingP6Factory` must be in the
`modulelist` to enable tracing.

```
modulelist=brave.p6spy.TracingP6Factory
url=jdbc:p6spy:derby:memory:p6spy;create=true
```

## Options
In addition to the required settings above, you can specify additional options to affect what is
included in the traces.

### Remove Service Name
By default the zipkin service name for your database is the name of the database.
Set the `remoteServiceName` property to override it:

```
remoteServiceName=myProductionDatabase
```

### Parameter values
When the `includeParameterValues` option is set to `true`, the tag `sql.query` will also include the
JDBC parameter values.

**Note**: if you enable this please also consider enabling `excludebinary` to avoid logging large
blob values as hex (see http://p6spy.readthedocs.io/en/latest/configandusage.html#excludebinary).

```
includeParameterValues=true
excludebinary=true
```

### Affected rows count
When the `includeAffectedRowsCount` option is set to `true`, the tag `sql.affected_rows` of traces
for SQL insert and update commands will include the number of rows that were inserted/updated, if
the database and driver supports that. No row count is included for select statements.

## Service name as URL query parameter
`spy.properties` applies globally to any instrumented jdbc connection. To override this, add the
`zipkinServiceName` property to your connection string.

```
jdbc:mysql://127.0.0.1:3306/mydatabase?zipkinServiceName=myServiceName
```

This will override the `remoteServiceName` set in `spy.properties`.

The current tracing component is used at runtime. Until you have instantiated `brave.Tracing`, no traces will appear.

## Filtering spans

By default, all statements are recorded as client spans.
You may wish to exclude statements like `set session` from tracing. This library reuses p6spy's log filtering for this purpose.
Filtering options are picked up from `spy.properties`, so you can blacklist/whitelist what type of statements to record as spans.

For configuration details please see p6spy's log filtering documentation.
