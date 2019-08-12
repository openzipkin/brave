/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.internal;

/** Internal utility class to validate IPv4 or IPv6 literals */
public final class IpLiteral {

  @Nullable public static String ipOrNull(@Nullable String ip) {
    if (ip == null || ip.isEmpty()) return null;
    if ("::1".equals(ip) || "127.0.0.1".equals(ip)) return ip; // special-case localhost
    IpFamily format = detectFamily(ip);
    if (format == IpFamily.IPv4Embedded) {
      ip = ip.substring(ip.lastIndexOf(':') + 1);
    } else if (format == IpFamily.Unknown) {
      ip = null;
    }
    return ip;
  }

  // All the below code is from zipkin2.Endpoint, copy/pasted here to prevent a depedency.
  enum IpFamily {
    Unknown,
    IPv4,
    IPv4Embedded,
    IPv6
  }

  /**
   * Adapted from code in {@code com.google.common.net.InetAddresses.ipStringToBytes}. This version
   * separates detection from parsing and checks more carefully about embedded addresses.
   */
  static IpFamily detectFamily(String ipString) {
    boolean hasColon = false;
    boolean hasDot = false;
    for (int i = 0, length = ipString.length(); i < length; i++) {
      char c = ipString.charAt(i);
      if (c == '.') {
        hasDot = true;
      } else if (c == ':') {
        if (hasDot) return IpFamily.Unknown; // Colons must not appear after dots.
        hasColon = true;
      } else if (notHex(c)) {
        return IpFamily.Unknown; // Everything else must be a decimal or hex digit.
      }
    }

    // Now decide which address family to parse.
    if (hasColon) {
      if (hasDot) {
        int lastColonIndex = ipString.lastIndexOf(':');
        if (!isValidIpV4Address(ipString, lastColonIndex + 1, ipString.length())) {
          return IpFamily.Unknown;
        }
        if (lastColonIndex == 1 && ipString.charAt(0) == ':') {// compressed like ::1.2.3.4
          return IpFamily.IPv4Embedded;
        }
        if (lastColonIndex != 6 || ipString.charAt(0) != ':' || ipString.charAt(1) != ':') {
          return IpFamily.Unknown;
        }
        for (int i = 2; i < 6; i++) {
          char c = ipString.charAt(i);
          if (c != 'f' && c != 'F' && c != '0') return IpFamily.Unknown;
        }
        return IpFamily.IPv4Embedded;
      }
      return IpFamily.IPv6;
    } else if (hasDot && isValidIpV4Address(ipString, 0, ipString.length())) {
      return IpFamily.IPv4;
    }
    return IpFamily.Unknown;
  }

  private static boolean notHex(char c) {
    return (c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F');
  }

  // Begin code from io.netty.util.NetUtil 4.1
  private static boolean isValidIpV4Address(String ip, int from, int toExcluded) {
    int len = toExcluded - from;
    int i;
    return len <= 15 && len >= 7 &&
      (i = ip.indexOf('.', from + 1)) > 0 && isValidIpV4Word(ip, from, i) &&
      (i = ip.indexOf('.', from = i + 2)) > 0 && isValidIpV4Word(ip, from - 1, i) &&
      (i = ip.indexOf('.', from = i + 2)) > 0 && isValidIpV4Word(ip, from - 1, i) &&
      isValidIpV4Word(ip, i + 1, toExcluded);
  }

  private static boolean isValidIpV4Word(CharSequence word, int from, int toExclusive) {
    int len = toExclusive - from;
    char c0, c1, c2;
    if (len < 1 || len > 3) return false;
    c0 = word.charAt(from);
    if (len == 3) {
      return (c1 = word.charAt(from + 1)) >= '0' &&
        (c2 = word.charAt(from + 2)) >= '0' &&
        ((c0 <= '1' && c1 <= '9' && c2 <= '9') ||
          (c0 == '2' && c1 <= '5' && (c2 <= '5' || (c1 < '5' && c2 <= '9'))));
    }
    return c0 <= '9' && (len == 1 || isValidNumericChar(word.charAt(from + 1)));
  }

  private static boolean isValidNumericChar(char c) {
    return c >= '0' && c <= '9';
  }
  // End code from io.netty.util.NetUtil 4.1
}
