package brave.internal.recorder;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MutableSpanTest {

  /** This just proves it is possible to not allocate anything while copying tags into a map */
  @Test
  public void showMapCanTakeTags() {
    MutableSpan span = new MutableSpan();
    span.tag("http.path", "/api");

    Map<String, String> map = new LinkedHashMap<>();
    span.forEachTag(Map::put, map);

    assertThat(map).containsEntry("http.path", "/api");
  }
}
