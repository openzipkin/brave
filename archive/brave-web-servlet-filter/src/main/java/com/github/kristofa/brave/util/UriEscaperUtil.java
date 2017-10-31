package com.github.kristofa.brave.util;

import java.net.URLEncoder;
import java.util.BitSet;
import java.util.stream.Collectors;


public class UriEscaperUtil
{

    final static BitSet dontNeedEncoding = new BitSet(256);

    static
    {
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

    public static String escape(String url)
    {
        return url.chars().mapToObj(UriEscaperUtil::convertChar).collect(Collectors.joining());
    }

    private static String convertChar(int inputChar)
    {
        if (dontNeedEncoding.get((char) inputChar))
        {
            return String.valueOf((char) inputChar);
        }
        else
        {
            return URLEncoder.encode(String.valueOf((char) inputChar));
        }
    }
}
