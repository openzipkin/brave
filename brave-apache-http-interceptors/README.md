# brave-apache-http-interceptors #

Http Request and Response interceptors that can be used with [Apache HttpClient](http://hc.apache.org/httpcomponents-client-4.3.x/index.html).

Apache HttpClient is probably the most known and used Java Http client. These interceptors make it easy
to integrate with brave to catch/trace client requests. The request interceptor will start a new
Span and submit cs (client sent) annotation. The response interceptor will submit cr (client received)
annotation.

Example of configuring interceptors with http client:

    final CloseableHttpClient httpclient =
            HttpClients.custom().addInterceptorFirst(new BraveHttpRequestInterceptor(clientTracer))
                .addInterceptorFirst(new BraveHttpResponseInterceptor(clientTracer)).build();

It is tested with httpclient version 4.3.3.