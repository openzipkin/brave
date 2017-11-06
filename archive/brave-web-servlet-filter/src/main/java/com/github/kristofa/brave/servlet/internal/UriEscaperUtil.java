package com.github.kristofa.brave.servlet.internal;

import java.net.URLEncoder;
import java.util.BitSet;

/**
 * Util executes extended URLEncoder flow, to omit converting list of particular symbols
 */
public class UriEscaperUtil {

    final static private BitSet dontNeedEncoding = new BitSet(256);

    static {
        /**
         * Safe chars list consumed from
         * <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/encodeURI">MDN</a>
         * as a excerpt from 2.2 of <a href="https://www.ietf.org/rfc/rfc2396.txt">RFC2396</a>
         */
        dontNeedEncoding.set(';');
        dontNeedEncoding.set(',');
        dontNeedEncoding.set('/');
        dontNeedEncoding.set('?');
        dontNeedEncoding.set(':');
        dontNeedEncoding.set('@');
        dontNeedEncoding.set('&');
        dontNeedEncoding.set('=');
        dontNeedEncoding.set('+');
        dontNeedEncoding.set('$');
        dontNeedEncoding.set('-');
        dontNeedEncoding.set('_');
        dontNeedEncoding.set('.');
        dontNeedEncoding.set('!');
        dontNeedEncoding.set('~');
        dontNeedEncoding.set('*');
        dontNeedEncoding.set('\'');
        dontNeedEncoding.set('(');
        dontNeedEncoding.set(')');
        dontNeedEncoding.set('#');
    }

    public static String escape(String url) {
        final StringBuilder result = new StringBuilder();
        for (char curCh : url.toCharArray()) {
            result.append(convertChar(curCh));
        }
        return result.toString();
    }

    private static String convertChar(char inputChar) {
        if (dontNeedEncoding.get(inputChar)) {
            return String.valueOf(inputChar);
        } else {
            return URLEncoder.encode(String.valueOf(inputChar));
        }
    }
}
