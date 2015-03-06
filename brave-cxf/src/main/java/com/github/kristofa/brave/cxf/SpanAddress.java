package com.github.kristofa.brave.cxf;

import org.apache.commons.lang.StringUtils;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import java.net.*;

/**
 * Created by fedor on 28.01.15.
 */
class SpanAddress
{
    String serviceName;
    String spanName;
    int localPort;
    String localIPv4;

    void parseNames(String uriPath)
    {
        if (uriPath.startsWith("/api/"))
            uriPath = uriPath.substring(4);
        if (uriPath.startsWith("/"))
            uriPath = uriPath.substring(1);
        int pos = uriPath.indexOf('/');
        if (pos > 0) {
            serviceName = uriPath.substring(0, pos);
            spanName = uriPath.substring(pos+1);
        } else {
            serviceName = "";
            spanName = uriPath;
        }
//        if (serviceName.isEmpty())
//            serviceName = uriPath;
//        if (spanName.isEmpty())
//            spanName = uriPath;
        if (serviceName.isEmpty())
            serviceName = "default";
        if (spanName.isEmpty())
            spanName = "index";
    }

    void parseIP(String localAddr)
    {
        localIPv4 = null;
        try {
            InetAddress ip4Addr = Inet4Address.getByName(localAddr);
            localIPv4 = ip4Addr.getHostAddress();
        } catch (UnknownHostException e) {
        }
        if (localIPv4 == null) {
            try {
                Inet6Address.getByName(localAddr);
                localIPv4 = "6.6.6.6";
            } catch (UnknownHostException e){
            }
        }
        if (localIPv4 == null)
            localIPv4 = "0.0.0.0";
    }


    public SpanAddress(HttpServletRequest request, String serviceNameOverride) {
        UrlPathHelper helper = new UrlPathHelper();

        parseNames(helper.getLookupPathForRequest(request));

        if (StringUtils.isNotBlank(serviceNameOverride))
            serviceName = serviceNameOverride;

        parseIP(request.getLocalAddr());

        localPort = request.getLocalPort();
    }

    public SpanAddress(HttpServletRequest request) {
        this(request, null);
    }

    public SpanAddress(URI uri, String serviceNameOverride) {
        parseNames(uri.getPath());

        if (StringUtils.isNotBlank(serviceNameOverride))
            serviceName = serviceNameOverride;

        parseIP(uri.getHost());

        localPort = uri.getPort();
        if (localPort < 0)
        {
            if ("http".equalsIgnoreCase(uri.getScheme()))
                localPort = 80;
            else if ("https".equalsIgnoreCase(uri.getScheme()))
                localPort = 443;
            else localPort = 0;
        }
    }

    public SpanAddress(URI uri) {
        this(uri, null);
    }

    public String getServiceName() { return serviceName; }

    public String getSpanName() { return spanName; }

    public String getLocalIPv4() {  return localIPv4; }

    public int getLocalPort() { return localPort; }
}
