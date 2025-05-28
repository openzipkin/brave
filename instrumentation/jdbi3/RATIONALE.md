# brave-instrumentation-jdbi3 rationale

## Floor JRE version

JDBI 3.x has a floor JRE version of 8.

## Default data policy

This is an alternative to [brave-instrumentation-mysql8][mysql8], so has the
same data policy. Changes to this should also be considered for MySQL as both
use JDBC underneath. That or extract a JDBC instrumentation base similar to
[brave-instrumentation-http][http], with helped `brave.Tag` implementations.

## Why not a `StatementContextListener`?

`StatementContextListener` has no way to get the exception for a failed
statement. Also, [brave-instrumentation-mongodb][mongodb] is similar code to
JDBI `StatementLogger`. Instrumentation that is similar is easier to maintain
over time.

## Why not a `Plugin`?

This replaces the JDBI `StatementLogger`, so should be used carefully. For
example, a `Plugin` might want to check if a `StatementLogger` already exists
and delegate to that.

## Why don't we parse the query parameter "zipkinServiceName"?

MySQL's driver initializes statically, so there is no mechanism to pass a
remote service name such as we do for all other instrumentation. In that case,
we add a query parameter "zipkinServiceName" to the connection URL.

JDBI is not initialized statically, so can avoid the overhead and complexity
of parsing a query parameter. Instead, we use the `TracingJdbiPlugin` to assign
this in the builder, like all other instrumentation.

## Why don't we use execution and completion moment from JDBI for the span?

JDBI exposes clock times like `ctx.getCompletionMoment().toEpochMilli()` which
could be considered to substitute for span start and duration. However, the
rest of Brave instrumentation are aligned with its clock, which is locked to
an epoch millisecond, with durations calculated from a monotonic source from
there.

If we used JDBI's clock, it would be inconsistent with the rest of
instrumentation, possibly starting before the parent span, and subject to
clock updates. Rather than attempt to heuristically align JDBI's clock, we use
brave's clock like everything else.

---
[mysql8]: ../mysql8/README.md
[http]: ../http/README.md
[mongodb]: ../mongodb/README.md
