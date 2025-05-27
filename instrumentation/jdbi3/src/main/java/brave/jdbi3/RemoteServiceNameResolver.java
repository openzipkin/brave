/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteServiceNameResolver {
    private static final Pattern serviceNamePattern = Pattern.compile("(^|&)zipkinServiceName=(?<serviceName>[^&]*)");
    private final Map<String, String> serviceNameCache = new ConcurrentHashMap<String, String>() { };

    public String resolve(String url) {
        return serviceNameCache.computeIfAbsent(url, key -> {
            if (key.startsWith("jdbc:")) {
                // strip "jdbc:" prefix
                key = key.substring(5);
            }
            URI uri = null; // strip "jdbc:"
            try {
                uri = new URI(key);
            } catch (URISyntaxException ignored) {
                return null;
            }

            String remoteServiceName = RemoteServiceNameResolver.extractRemoteServiceName(uri);
            serviceNameCache.put(key, remoteServiceName);
            return remoteServiceName;
        });
    }

    static String extractRemoteServiceName(URI uri) {
        String remoteServiceNameFromQueryParameter = getRemoteServiceNameFromQueryParameter(uri);
        if (remoteServiceNameFromQueryParameter != null) {
            return remoteServiceNameFromQueryParameter;
        }

        if (uri.getAuthority() != null) {
            return uri.getAuthority();
        }

        if (uri.getScheme() != null) {
            return uri.getScheme();
        }

        return null;
    }

    static String getRemoteServiceNameFromQueryParameter(URI uri) {
        String query = uri.getQuery();
        if (query != null) {
            Matcher matcher = serviceNamePattern.matcher(query);
            if (matcher.find()) {
                return matcher.group("serviceName");
            }
        }

        try {
            URI schemeSpecificUri = new URI(uri.getSchemeSpecificPart());
            if (schemeSpecificUri.toString().length() < uri.toString().length()) {
                return getRemoteServiceNameFromQueryParameter(schemeSpecificUri);
            }
        } catch (URISyntaxException ignored) {
            return null;
        }

        return null;
    }
}
