# brave-instrumentation-dubbo rationale
See [RPC](../rpc/RATIONALE.md) for general RPC rationale.

## Error code words not numbers
As per normal rationale, we use code names, not numbers, for Dubbo, as defined
in `RpcException`'s constants. In short, this makes trace data more intuitive
and easier to search.
