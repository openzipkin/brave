# brave-instrumentation-dubbo rationale

## Error code words not numbers
Similar to [RPC](../rpc/RATIONALE.md), we use error code names, not numbers in
Dubbo.

HTTP status codes are grouped by classification, and have existed so long that
support teams can usually identify meaning by looking at a number like 401.
Being triple digits, it is relatively easy to search for what an HTTP status
means.

Dubbo code numbers are more like enum ordinals. There's no grouping and it is
not easy to identify a problem quickly by seeing a number like 2 vs the code
name "TIMEOUT_EXCEPTION". There is no generic documentation on Dubbo errors. If
given only the number 2, a user unfamiliar with how Dubbo works internally will
have a hard time. For example, searching Dubbo's code base for "2" will return
less relevant results than searching for "TIMEOUT_EXCEPTION".

It may seem that exception messages can overcome this problem, and they
certainly can when present. Also, exception messages can be localized. However,
exception messages are not good for trace search because they are long and
contain variables.

For all these reasons, we use code names, not numbers, for Dubbo, as defined in
`RpcException`'s constants (so that they are easy to search).
