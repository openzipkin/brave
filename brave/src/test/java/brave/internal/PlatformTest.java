/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal;

import brave.internal.codec.HexCodec;
import com.google.common.collect.Sets;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformTest {
  @Test void clock_hasNiceToString_jre7() {
    Platform platform = new Platform.Jre7();

    assertThat(platform.clock()).hasToString("System.currentTimeMillis()");
  }

  @Test void clock_hasNiceToString_jre9() {
    Platform platform = new Platform.Jre9();

    assertThat(platform.clock()).hasToString("Clock.systemUTC().instant()");
  }

  // example from X-Amzn-Trace-Id: Root=1-5759e988-bd862e3fe1be46a994272793;Sampled=1
  @Test void randomLong_epochSecondsPlusRandom() {
    Platform platform = new Platform.Jre7() {
      @Override public long currentTimeMicroseconds() {
        return 1465510280000000L; // Thursday, June 9, 2016 10:11:20 PM
      }
    };

    long traceIdHigh = platform.nextTraceIdHigh();

    assertThat(HexCodec.toLowerHex(traceIdHigh)).startsWith("5759e988");
  }

  @Test void randomLong_whenRandomIsMostNegative() {
    long traceIdHigh = Platform.nextTraceIdHigh(1465510280000000L, 0xffffffff);

    assertThat(HexCodec.toLowerHex(traceIdHigh)).isEqualTo("5759e988ffffffff");
  }

  @Test void linkLocalIp_lazySet() {
    Platform platform = Platform.findPlatform(); // not get as it caches nics.
    assertThat(platform.linkLocalIp).isNull(); // sanity check setup

    // cannot test as the there is no link local IP
    if (platform.produceLinkLocalIp() == null) return;

    assertThat(platform.linkLocalIp()).isNotNull();
  }

  @Test void linkLocalIp_sameInstance() {
    Platform platform = new Platform.Jre7();

    assertThat(platform.linkLocalIp()).isSameAs(platform.linkLocalIp());
  }

  @Test void produceLinkLocalIp_exceptionReadingNics() {
    try (MockedStatic<NetworkInterface> mb = mockStatic(NetworkInterface.class)) {
      mb.when(NetworkInterface::getNetworkInterfaces).thenThrow(SocketException.class);

      Platform platform = Platform.findPlatform(); // not get as it caches nics.
      assertThat(platform.produceLinkLocalIp()).isNull();
    }
  }

  /** possible albeit very unlikely */
  @Test void produceLinkLocalIp_noNics() {
    try (MockedStatic<NetworkInterface> mb = mockStatic(NetworkInterface.class)) {
      mb.when(NetworkInterface::getNetworkInterfaces).thenReturn(null);

      Platform platform = Platform.findPlatform(); // not get as it caches nics.
      assertThat(platform.linkLocalIp()).isNull();

      mb.when(NetworkInterface::getNetworkInterfaces)
        .thenReturn(new Vector<NetworkInterface>().elements());

      assertThat(platform.produceLinkLocalIp()).isNull();
    }
  }

  /** also possible albeit unlikely */
  @Test void produceLinkLocalIp_noAddresses() {
    try (MockedStatic<NetworkInterface> mb = mockStatic(NetworkInterface.class)) {
      Enumeration<NetworkInterface> nics = nicsWithAddress(null);
      mb.when(NetworkInterface::getNetworkInterfaces).thenReturn(nics);

      Platform platform = Platform.findPlatform(); // not get as it caches nics.
      assertThat(platform.produceLinkLocalIp()).isNull();
    }
  }

  @Test void produceLinkLocalIp_siteLocal_ipv4() throws Exception {
    try (MockedStatic<NetworkInterface> mb = mockStatic(NetworkInterface.class)) {
      InetAddress local =
        InetAddress.getByAddress("local", new byte[] {(byte) 192, (byte) 168, 0, 1});
      Enumeration<NetworkInterface> nics = nicsWithAddress(local);
      mb.when(NetworkInterface::getNetworkInterfaces).thenReturn(nics);

      Platform platform = Platform.findPlatform(); // not get as it caches nics.
      assertThat(platform.produceLinkLocalIp()).isEqualTo("192.168.0.1");
    }
  }

  @Test void produceLinkLocalIp_siteLocal_ipv6() throws Exception {
    try (MockedStatic<NetworkInterface> mb = mockStatic(NetworkInterface.class)) {
      InetAddress ipv6 = Inet6Address.getByName("fec0:db8::c001");
      Enumeration<NetworkInterface> nics = nicsWithAddress(ipv6);
      mb.when(NetworkInterface::getNetworkInterfaces).thenReturn(nics);

      Platform platform = Platform.findPlatform(); // not get as it caches nics.
      assertThat(platform.produceLinkLocalIp()).isEqualTo(ipv6.getHostAddress());
    }
  }

  @Test void produceLinkLocalIp_notSiteLocal_ipv4() throws Exception {
    try (MockedStatic<NetworkInterface> mb = mockStatic(NetworkInterface.class)) {
      InetAddress external = InetAddress.getByAddress("external", new byte[] {1, 2, 3, 4});
      Enumeration<NetworkInterface> nics = nicsWithAddress(external);
      mb.when(NetworkInterface::getNetworkInterfaces).thenReturn(nics);

      Platform platform = Platform.findPlatform(); // not get as it caches nics.
      assertThat(platform.produceLinkLocalIp()).isNull();
    }
  }

  @Test void produceLinkLocalIp_notSiteLocal_ipv6() throws Exception {
    try (MockedStatic<NetworkInterface> mb = mockStatic(NetworkInterface.class)) {
      InetAddress addr = Inet6Address.getByName("2001:db8::c001");
      Enumeration<NetworkInterface> nics = nicsWithAddress(addr);
      mb.when(NetworkInterface::getNetworkInterfaces).thenReturn(nics);

      Platform platform = Platform.findPlatform(); // not get as it caches nics.
      assertThat(platform.produceLinkLocalIp()).isNull();
    }
  }

  /**
   * Getting an endpoint is expensive. This tests it is provisioned only once.
   * <p>
   * test inspired by dagger.internal.DoubleCheckTest
   */
  @Test void linkLocalIp_provisionsOnce() throws Exception {
    Platform platform = Platform.findPlatform(); // not get as it caches nics.

    // create all the tasks up front so that they are executed with no delay
    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      tasks.add(platform::linkLocalIp);
    }

    ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
    List<Future<String>> futures = executor.invokeAll(tasks);

    // check there's only a single unique endpoint returned
    Set<Object> results = Sets.newIdentityHashSet();
    for (Future<String> future : futures) {
      results.add(future.get());
    }
    assertThat(results).hasSize(1);

    executor.shutdownNow();
  }

  static Enumeration<NetworkInterface> nicsWithAddress(@Nullable InetAddress address) {
    Vector<InetAddress> addresses = new Vector<>();
    if (address != null) addresses.add(address);
    NetworkInterface nic = mock(NetworkInterface.class);
    Vector<NetworkInterface> nics = new Vector<>();
    nics.add(nic);
    when(nic.getInetAddresses()).thenReturn(addresses.elements());
    return nics.elements();
  }
}
