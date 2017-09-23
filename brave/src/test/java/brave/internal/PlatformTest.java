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
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import zipkin2.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
// Added to declutter console: tells power mock not to mess with implicit classes we aren't testing
@PowerMockIgnore({"org.apache.logging.*", "javax.script.*"})
@PrepareForTest({Platform.class, NetworkInterface.class})
public class PlatformTest {
  Endpoint unknownEndpoint = Endpoint.newBuilder().serviceName("unknown").build();
  Platform platform = Platform.Jre7.buildIfSupported(true);

  @Test public void relativeTimestamp_incrementsAccordingToNanoTick() {
    mockStatic(System.class);
    when(System.currentTimeMillis()).thenReturn(0L);
    when(System.nanoTime()).thenReturn(0L);

    Platform platform = new Platform() {
      @Override public boolean zipkinV1Present() {
        return true;
      }

      @Override public long randomLong() {
        return 1L;
      }
    };

    when(System.nanoTime()).thenReturn(1000L); // 1 microsecond

    assertThat(platform.currentTimeMicroseconds()).isEqualTo(1);
  }

  @Test public void zipkinV1Absent() throws ClassNotFoundException {
    mockStatic(Class.class);
    when(Class.forName(zipkin.Endpoint.class.getName()))
        .thenThrow(ClassNotFoundException.class);

    assertThat(Platform.findPlatform().zipkinV1Present())
        .isFalse();
  }

  @Test public void localEndpoint_lazySet() {
    assertThat(platform.localEndpoint).isNull(); // sanity check setup

    assertThat(platform.localEndpoint())
        .isNotNull();
  }

  @Test public void localEndpoint_sameInstance() {
    assertThat(platform.localEndpoint())
        .isSameAs(platform.localEndpoint());
  }

  @Test public void produceLocalEndpoint_exceptionReadingNics() throws Exception {
    mockStatic(NetworkInterface.class);
    when(NetworkInterface.getNetworkInterfaces()).thenThrow(SocketException.class);

    assertThat(platform.produceLocalEndpoint())
        .isEqualTo(unknownEndpoint);
  }

  /** possible albeit very unlikely */
  @Test public void produceLocalEndpoint_noNics() throws Exception {
    mockStatic(NetworkInterface.class);

    when(NetworkInterface.getNetworkInterfaces())
        .thenReturn(null);

    assertThat(platform.localEndpoint())
        .isEqualTo(unknownEndpoint);

    when(NetworkInterface.getNetworkInterfaces())
        .thenReturn(new Vector<NetworkInterface>().elements());

    assertThat(platform.produceLocalEndpoint())
        .isEqualTo(unknownEndpoint);
  }

  /** also possible albeit unlikely */
  @Test public void produceLocalEndpoint_noAddresses() throws Exception {
    nicWithAddress(null);

    assertThat(platform.produceLocalEndpoint())
        .isEqualTo(unknownEndpoint);
  }

  @Test public void produceLocalEndpoint_siteLocal_ipv4() throws Exception {
    nicWithAddress(InetAddress.getByAddress("local", new byte[] {(byte) 192, (byte) 168, 0, 1}));

    assertThat(platform.produceLocalEndpoint())
        .isEqualTo(unknownEndpoint.toBuilder().ip("192.168.0.1").build());
  }

  @Test public void produceLocalEndpoint_siteLocal_ipv6() throws Exception {
    InetAddress ipv6 = Inet6Address.getByName("fec0:db8::c001");
    nicWithAddress(ipv6);

    assertThat(platform.produceLocalEndpoint())
        .isEqualTo(unknownEndpoint.toBuilder().ip(ipv6).build());
  }

  @Test public void produceLocalEndpoint_notSiteLocal_ipv4() throws Exception {
    nicWithAddress(InetAddress.getByAddress("external", new byte[] {1, 2, 3, 4}));

    assertThat(platform.produceLocalEndpoint())
        .isEqualTo(unknownEndpoint);
  }

  @Test public void produceLocalEndpoint_notSiteLocal_ipv6() throws Exception {
    nicWithAddress(Inet6Address.getByName("2001:db8::c001"));

    assertThat(platform.produceLocalEndpoint())
        .isEqualTo(unknownEndpoint);
  }

  /**
   * Getting an endpoint is expensive. This tests it is provisioned only once.
   *
   * test inspired by dagger.internal.DoubleCheckTest
   */
  @Test
  public void localEndpoint_provisionsOnce() throws Exception {
    // create all the tasks up front so that they are executed with no delay
    List<Callable<Endpoint>> tasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      tasks.add(() -> platform.localEndpoint());
    }

    ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
    List<Future<Endpoint>> futures = executor.invokeAll(tasks);

    // check there's only a single unique endpoint returned
    Set<Object> results = Sets.newIdentityHashSet();
    for (Future<Endpoint> future : futures) {
      results.add(future.get());
    }
    assertThat(results).hasSize(1);

    executor.shutdownNow();
  }

  static void nicWithAddress(@Nullable InetAddress address) throws SocketException {
    mockStatic(NetworkInterface.class);
    Vector<InetAddress> addresses = new Vector<>();
    if (address != null) addresses.add(address);
    NetworkInterface nic = PowerMockito.mock(NetworkInterface.class);
    Vector<NetworkInterface> nics = new Vector<>();
    nics.add(nic);
    when(NetworkInterface.getNetworkInterfaces()).thenReturn(nics.elements());
    when(nic.getInetAddresses()).thenReturn(addresses.elements());
  }
}
