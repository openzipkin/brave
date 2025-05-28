# brave-instrumentation-jdbi3

This includes [TracingSqlLogger][TracingSqlLogger] for the Jdbi instance that
reports via Brave how long each query takes, along with relevant tags like the
query.

Example Usage:
```java
SqlLogger sqlLogger = JdbiTracing.create(tracing).sqlLogger();
jdbi.getConfig(SqlStatements.class).setSqlLogger(sqlLogger);
```

---
[TracingSqlLogger]: src/main/java/brave/jdbi3/TracingSqlLogger.java
