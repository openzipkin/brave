package zipkin.internal;

import com.github.kristofa.brave.IdConversion;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import zipkin.Span;

import static java.util.Arrays.asList;

/** In internal package to access zipkin internal code */
public class TraceUtil {
  public static List<Span> washIds(List<Span> trace) {
    // we want to return spans in their original order
    Map<Span, Span> map = new LinkedHashMap<>();
    for (Span span : trace) map.put(span, span);

    long traceId = 1L, id = 1L;
    // traverse the tree breadth-first, and replace the ids with incrementing ones
    for (Iterator<Node<Span>> iter = Node.constructTree(trace).traverse(); iter.hasNext(); id++) {
      Node<Span> next = iter.next();
      if (next.parent() == null) {
        Span root = next.value().toBuilder().traceId(traceId).id(id).build();
        map.replace(next.value(), root);
        next.value(root);
      } else {
        Span parent = next.parent().value();
        Span child = next.value().toBuilder().traceId(traceId).parentId(parent.id).id(id).build();
        map.replace(next.value(), child);
        next.value(child);
      }
    }
    return new ArrayList<>(map.values());
  }

  /** washes propagated trace identifiers in the request headers */
  public static Map<String, List<String>> washIds(Map<String, List<String>> headers,
      List<Span> unwashed) {
    List<Span> washed = washIds(unwashed);
    Map<String, String> idMapping = new LinkedHashMap<>();
    for (int i = 0; i < unwashed.size(); i++) {
      idMapping.put(
          IdConversion.convertToString(unwashed.get(i).id),
          IdConversion.convertToString(washed.get(i).id)
      );
    }

    Map<String, List<String>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    result.putAll(headers);
    for (Map.Entry<String, List<String>> entry : result.entrySet()) {
      String replacement = idMapping.get(entry.getValue().get(0));
      if (replacement != null) {
        result.put(entry.getKey(), asList(replacement));
      }
    }
    return result;
  }
}
