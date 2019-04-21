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

In addition, you can specify the following options in spy.properties

`remoteServiceName`

By default the zipkin service name for your database is the name of the database. Set this property to override it

```
remoteServiceName=myProductionDatabase
```

`includeParameterValues`

When set to to true, the tag `sql.query` will also include the JDBC parameter values.
 
**Note**: if you enable this please also consider enabling 'excludebinary' to avoid logging large blob values as hex (see http://p6spy.readthedocs.io/en/latest/configandusage.html#excludebinary).

```  
includeParameterValues=true
excludebinary=true
```

`spy.properties` applies globally to any instrumented jdbc connection. To override this, add the `zipkinServiceName` property to your connection string.

```
jdbc:mysql://127.0.0.1:3306/mydatabase?zipkinServiceName=myServiceName
```

This will override the `remoteServiceName` set in `spy.properties`.

The current tracing component is used at runtime. Until you have instantiated `brave.Tracing`, no traces will appear.

### Filtering spans

By default, all statements are recorded as client spans. 
You may wish to exclude statements like `set session` from tracing. This library reuses p6spy's log filtering for this purpose.
Filtering options are picked up from `spy.properties`, so you can blacklist/whitelist what type of statements to record as spans.

For configuration details please see p6spy's log filtering documentation.
