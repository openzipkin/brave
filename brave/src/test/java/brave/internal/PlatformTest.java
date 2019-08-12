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

import com.google.common.collect.Sets;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
// Added to declutter console: tells power mock not to mess with implicit classes we aren't testing
@PowerMockIgnore({"org.apache.logging.*", "javax.script.*"})
@PrepareForTest({Platform.class, NetworkInterface.class})
public class PlatformTest {
  Platform platform = new Platform.Jre7();

  @Test public void clock_hasNiceToString_jre7() {
    assertThat(platform.clock())
      .hasToString("System.currentTimeMillis()");
  }

  @Test public void clock_hasNiceToString_jre9() {
    Platform platform = new Platform.Jre9();

    assertThat(platform.clock())
      .hasToString("Clock.systemUTC().instant()");
  }

  // example from X-Amzn-Trace-Id: Root=1-5759e988-bd862e3fe1be46a994272793;Sampled=1
  @Test public void randomLong_epochSecondsPlusRandom() {
    mockStatic(System.class);
    when(System.currentTimeMillis())
      .thenReturn(1465510280_000L); // Thursday, June 9, 2016 10:11:20 PM

    long traceIdHigh = platform.nextTraceIdHigh();

    assertThat(HexCodec.toLowerHex(traceIdHigh)).startsWith("5759e988");
  }

  @Test public void randomLong_whenRandomIsMostNegative() {
    mockStatic(System.class);
    when(System.currentTimeMillis()).thenReturn(1465510280_000L);

    long traceIdHigh = Platform.nextTraceIdHigh(0xffffffff);

    assertThat(HexCodec.toLowerHex(traceIdHigh))
      .isEqualTo("5759e988ffffffff");
  }

  @Test public void linkLocalIp_lazySet() {
    assertThat(platform.linkLocalIp).isNull(); // sanity check setup

    // cannot test as the there is no link local IP
    if (platform.produceLinkLocalIp() == null) return;

    assertThat(platform.linkLocalIp())
      .isNotNull();
  }

  @Test public void linkLocalIp_sameInstance() {
    assertThat(platform.linkLocalIp())
      .isSameAs(platform.linkLocalIp());
  }

  @Test public void produceLinkLocalIp_exceptionReadingNics() throws Exception {
    mockStatic(NetworkInterface.class);
    when(NetworkInterface.getNetworkInterfaces()).thenThrow(SocketException.class);

    assertThat(platform.produceLinkLocalIp())
      .isNull();
  }

  /** possible albeit very unlikely */
  @Test public void produceLinkLocalIp_noNics() throws Exception {
    mockStatic(NetworkInterface.class);

    when(NetworkInterface.getNetworkInterfaces())
      .thenReturn(null);

    assertThat(platform.linkLocalIp())
      .isNull();

    when(NetworkInterface.getNetworkInterfaces())
      .thenReturn(new Vector<NetworkInterface>().elements());

    assertThat(platform.produceLinkLocalIp())
      .isNull();
  }

  /** also possible albeit unlikely */
  @Test public void produceLinkLocalIp_noAddresses() throws Exception {
    nicWithAddress(null);

    assertThat(platform.produceLinkLocalIp())
      .isNull();
  }

  @Test public void produceLinkLocalIp_siteLocal_ipv4() throws Exception {
    nicWithAddress(InetAddress.getByAddress("local", new byte[] {(byte) 192, (byte) 168, 0, 1}));

    assertThat(platform.produceLinkLocalIp())
      .isEqualTo("192.168.0.1");
  }

  @Test public void produceLinkLocalIp_siteLocal_ipv6() throws Exception {
    InetAddress ipv6 = Inet6Address.getByName("fec0:db8::c001");
    nicWithAddress(ipv6);

    assertThat(platform.produceLinkLocalIp())
      .isEqualTo(ipv6.getHostAddress());
  }

  @Test public void produceLinkLocalIp_notSiteLocal_ipv4() throws Exception {
    nicWithAddress(InetAddress.getByAddress("external", new byte[] {1, 2, 3, 4}));

    assertThat(platform.produceLinkLocalIp())
      .isNull();
  }

  @Test public void produceLinkLocalIp_notSiteLocal_ipv6() throws Exception {
    nicWithAddress(Inet6Address.getByName("2001:db8::c001"));

    assertThat(platform.produceLinkLocalIp())
      .isNull();
  }

  /**
   * Getting an endpoint is expensive. This tests it is provisioned only once.
   *
   * test inspired by dagger.internal.DoubleCheckTest
   */
  @Test
  public void linkLocalIp_provisionsOnce() throws Exception {
    // create all the tasks up front so that they are executed with no delay
    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      tasks.add(() -> platform.linkLocalIp());
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

  static void nicWithAddress(@Nullable InetAddress address) throws SocketException {
    mockStatic(NetworkInterface.class);
    Vector<InetAddress> addresses = new Vector<>();
    if (address != null) addresses.add(address);
    NetworkInterface nic = mock(NetworkInterface.class);
    Vector<NetworkInterface> nics = new Vector<>();
    nics.add(nic);
    when(NetworkInterface.getNetworkInterfaces()).thenReturn(nics.elements());
    when(nic.getInetAddresses()).thenReturn(addresses.elements());
  }
}
