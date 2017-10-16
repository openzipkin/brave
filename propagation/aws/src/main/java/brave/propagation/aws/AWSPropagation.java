package brave.propagation.aws;

import brave.internal.Nullable;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.TraceIdContext;
import java.util.Collections;
import java.util.List;

import static brave.internal.HexCodec.writeHexByte;
import static brave.internal.HexCodec.writeHexLong;

/**
 * Utility for working with Amazon Web Services Trace IDs, for example reading from headers or
 * environment variables. {@code x-amzn-trace-id} is primarily for Amazon's X-Ray service, but it is
 * also integrated with AWS ALB, API Gateway and Lambda.
 *
 * <p>For example, if you are in a lambda environment, you can read the incoming context like this:
 * <pre>{@code
 * extracted = AWSPropagation.extract(System.getenv("_X_AMZN_TRACE_ID"));
 * }</pre>
 *
 * <h3>Details</h3> {@code x-amzn-trace-id} or {@code _X_AMZN_TRACE_ID} follows RFC 6265 style
 * syntax (https://tools.ietf.org/html/rfc6265#section-2.2): fields are split on semicolon and
 * optional whitespace.
 *
 * <p>Description of the {@code Root} (or {@code Self}) field from AWS CLI help:
 *
 * <p>A trace_id consists of three numbers separated by hyphens. For example, {@code
 * 1-58406520-a006649127e371903a2de979}. This includes:
 * <pre>
 * <ul>
 * <li>The version number, i.e. 1</li>
 * <li>The time of the original request, in Unix epoch time, in 8  hexadecimal digits. For example,
 * 10:00AM December 2nd, 2016 PST in epoch timeis 1480615200 seconds, or 58406520 in
 * hexadecimal.</li>
 * <li>A 96-bit identifier for the trace, globally unique, in 24 hexadecimal digits.</li>
 * </ul>
 * </pre>
 */
public final class AWSPropagation<K> implements Propagation<K> {

  public static final Propagation.Factory FACTORY = new Propagation.Factory() {
    @Override public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
      return new AWSPropagation<>(keyFactory);
    }

    @Override public boolean requires128BitTraceId() {
      return true;
    }

    @Override public String toString() {
      return "AWSPropagationFactory";
    }
  };

  // Using lowercase field name as http is case-insensitive, but http/2 transport downcases */
  static final String TRACE_ID_NAME = "x-amzn-trace-id";
  static final char[] ROOT = "Root=".toCharArray();
  static final char[] PARENT = ";Parent=".toCharArray();
  static final char[] SAMPLED = ";Sampled=".toCharArray();

  final K traceIdKey;
  final List<K> fields;

  AWSPropagation(KeyFactory<K> keyFactory) {
    this.traceIdKey = keyFactory.create(TRACE_ID_NAME);
    this.fields = Collections.singletonList(traceIdKey);
  }

  /** returns the name of the header field: "x-amzn-trace-id" */
  @Override public List<K> keys() {
    return fields;
  }

  @Override public <C> TraceContext.Injector<C> injector(Setter<C, K> setter) {
    if (setter == null) throw new NullPointerException("setter == null");
    return new AWSInjector<>(this, setter);
  }

  static final class AWSInjector<C, K> implements TraceContext.Injector<C> {
    final AWSPropagation<K> propagation;
    final Setter<C, K> setter;

    AWSInjector(AWSPropagation<K> propagation, Setter<C, K> setter) {
      this.propagation = propagation;
      this.setter = setter;
    }

    /**
     * This version of propagation contains at least 74 characters corresponding to identifiers and
     * the sampling bit. It will also include extra fields where present.
     *
     * <p>Ex 74 characters: {@code Root=1-67891233-abcdef012345678912345678;Parent=463ac35c9f6413ad;Sampled=1}
     *
     * <p>{@inheritDoc}
     */
    @Override public void inject(TraceContext traceContext, C carrier) {
      CharSequence extra = null;
      for (int i = 0, length = traceContext.extra().size(); i < length; i++) {
        Object next = traceContext.extra().get(i);
        if (next instanceof Extra) {
          extra = ((Extra) next).fields;
          break;
        }
      }
      int extraLength = extra == null ? 0 : extra.length();
      //Root=1-67891233-abcdef012345678912345678;Parent=463ac35c9f6413ad;Sampled=1
      char[] result = new char[74 + extraLength];
      System.arraycopy(ROOT, 0, result, 0, 5);
      writeRoot(traceContext, result, 5);
      System.arraycopy(PARENT, 0, result, 40, 8);
      writeHexLong(result, 48, traceContext.spanId());
      System.arraycopy(SAMPLED, 0, result, 64, 9);
      Boolean sampled = traceContext.sampled();
      // Sampled status is same as B3, but ? means downstream decides (like omitting X-B3-Sampled)
      // https://github.com/aws/aws-xray-sdk-go/blob/391885218b556c43ed05a1e736a766d70fc416f1/header/header.go#L50
      result[73] = sampled == null ? '?' : sampled ? '1' : '0';
      for (int i = 0; i < extraLength; i++) {
        result[i + 74] = extra.charAt(i);
      }
      setter.put(carrier, propagation.traceIdKey, new String(result));
    }
  }

  /**
   * Used for log correlation or {@link brave.Span#tag(String, String) tag values}
   *
   * @return a formatted Root field like "1-58406520-a006649127e371903a2de979" or null if the
   * context was not created from an instance of {@link AWSPropagation}.
   */
  @Nullable public static String rootField(TraceContext context) {
    for (int i = 0, length = context.extra().size(); i < length; i++) {
      Object next = context.extra().get(i);
      if (next instanceof Extra) {
        char[] result = new char[35];
        writeRoot(context, result, 0);
        return new String(result);
      }
    }
    return null;
  }

  /** Writes 35 characters representing the input trace ID to the buffer at the given offset */
  static void writeRoot(TraceContext context, char[] result, int offset) {
    result[offset] = '1'; // version
    result[offset + 1] = '-'; // delimiter
    long high = context.traceIdHigh();
    writeHexByte(result, offset + 2, (byte) ((high >>> 56L) & 0xff));
    writeHexByte(result, offset + 4, (byte) ((high >>> 48L) & 0xff));
    writeHexByte(result, offset + 6, (byte) ((high >>> 40L) & 0xff));
    writeHexByte(result, offset + 8, (byte) ((high >>> 32L) & 0xff));
    result[offset + 10] = '-';
    writeHexByte(result, offset + 11, (byte) ((high >>> 24L) & 0xff));
    writeHexByte(result, offset + 13, (byte) ((high >>> 16L) & 0xff));
    writeHexByte(result, offset + 15, (byte) ((high >>> 8L) & 0xff));
    writeHexByte(result, offset + 17, (byte) (high & 0xff));
    writeHexLong(result, offset + 19, context.traceId());
  }

  @Override public <C> TraceContext.Extractor<C> extractor(Getter<C, K> getter) {
    if (getter == null) throw new NullPointerException("getter == null");
    return new AWSExtractor(this, getter);
  }

  static final AWSExtractor<String, String> STRING_EXTRACTOR =
      new AWSExtractor<>(new AWSPropagation<>(KeyFactory.STRING), (carrier, key) -> carrier);

  /**
   * Like {@link TraceContext.Extractor#extract(Object)} except reading from a single field.
   *
   * <p>This is used for extracting from the AWS lambda environment variable {@code
   * X_AMZN_TRACE_ID}.
   */
  public static TraceContextOrSamplingFlags extract(String amznTraceId) {
    if (amznTraceId == null) throw new NullPointerException("amznTraceId == null");
    return STRING_EXTRACTOR.extract(amznTraceId);
  }

  static final class AWSExtractor<C, K> implements TraceContext.Extractor<C> {
    final AWSPropagation<K> propagation;
    final Getter<C, K> getter;

    AWSExtractor(AWSPropagation<K> propagation, Getter<C, K> getter) {
      this.propagation = propagation;
      this.getter = getter;
    }

    enum Op {
      SKIP,
      ROOT,
      PARENT,
      SAMPLED,
      EXTRA
    }

    @Override public TraceContextOrSamplingFlags extract(C carrier) {
      if (carrier == null) throw new NullPointerException("carrier == null");
      String traceIdString = getter.get(carrier, propagation.traceIdKey);
      if (traceIdString == null) return TraceContextOrSamplingFlags.EMPTY;

      Boolean sampled = null;
      long traceIdHigh = 0L, traceId = 0L;
      Long parent = null;
      StringBuilder currentString = new StringBuilder(7 /* Sampled.length */), extraFields = null;
      Op op = null;
      OUTER:
      for (int i = 0, length = traceIdString.length(); i < length; i++) {
        char c = traceIdString.charAt(i);
        if (c == ' ') continue; // trim whitespace
        if (c == '=') { // we reached a field name
          if (++i == length) break; // skip '=' character
          if (currentString.indexOf("Root") == 0) {
            op = Op.ROOT;
          } else if (currentString.indexOf("Parent") == 0) {
            op = Op.PARENT;
          } else if (currentString.indexOf("Sampled") == 0) {
            op = Op.SAMPLED;
          } else if (currentString.indexOf("Self") == 0) {
            // ALB implements Trace ID chaining using self so that customers not using X-Ray
            // (I.e. request logs) can do the correlation themselves. We drop these
            op = Op.SKIP;
          } else {
            op = Op.EXTRA;
            if (extraFields == null) extraFields = new StringBuilder();
            extraFields.append(';').append(currentString);
          }
          currentString.setLength(0);
        } else if (op == null) {
          currentString.append(c);
          continue;
        }
        // no longer whitespace
        switch (op) {
          case EXTRA:
            extraFields.append(c);
            while (i < length && (c = traceIdString.charAt(i)) != ';') {
              extraFields.append(c);
              i++;
            }
            break;
          case SKIP:
            while (++i < length && traceIdString.charAt(i) != ';') {
              // skip until we hit a delimiter
            }
            break;
          case ROOT:
            if (i + 35 > length // 35 = length of 1-67891233-abcdef012345678912345678
                || traceIdString.charAt(i++) != '1'
                || traceIdString.charAt(i++) != '-') {
              break OUTER; // invalid version or format
            }
            // Parse the epoch seconds and high 32 of the 96 bit trace ID into traceID high
            for (int hyphenIndex = i + 8, endIndex = hyphenIndex + 1 + 8; i < endIndex; i++) {
              c = traceIdString.charAt(i);
              if (c == '-' && i == hyphenIndex) continue; // skip delimiter between epoch and random
              traceIdHigh <<= 4;
              if (c >= '0' && c <= '9') {
                traceIdHigh |= c - '0';
              } else if (c >= 'a' && c <= 'f') {
                traceIdHigh |= c - 'a' + 10;
              } else {
                break OUTER; // invalid format
              }
            }
            // Parse the low 64 of the 96 bit trace ID into traceId
            for (int endIndex = i + 16; i < endIndex; i++) {
              c = traceIdString.charAt(i);
              traceId <<= 4;
              if (c >= '0' && c <= '9') {
                traceId |= c - '0';
              } else if (c >= 'a' && c <= 'f') {
                traceId |= c - 'a' + 10;
              } else {
                break OUTER; // invalid format
              }
            }
            break;
          case PARENT:
            long parentId = 0L;
            for (int endIndex = i + 16; i < endIndex; i++) {
              c = traceIdString.charAt(i);
              parentId <<= 4;
              if (c >= '0' && c <= '9') {
                parentId |= c - '0';
              } else if (c >= 'a' && c <= 'f') {
                parentId |= c - 'a' + 10;
              } else {
                break OUTER; // invalid format
              }
            }
            parent = parentId;
            break;
          case SAMPLED:
            c = traceIdString.charAt(i++);
            if (c == '1') {
              sampled = true;
            } else if (c == '0') {
              sampled = false;
            }
            break;
        }
        op = null;
      }

      List<Object> extra;
      if (extraFields == null) {
        extra = DEFAULT_EXTRA;
      } else {
        Extra e = new Extra();
        e.fields = extraFields;
        extra = Collections.singletonList(e);
      }

      if (traceIdHigh == 0L) { // traceIdHigh cannot be null, so just return sampled
        return TraceContextOrSamplingFlags.newBuilder()
            .extra(extra)
            .samplingFlags(SamplingFlags.Builder.build(sampled))
            .build();
      } else if (parent == null) {
        return TraceContextOrSamplingFlags.newBuilder()
            .extra(extra)
            .traceIdContext(TraceIdContext.newBuilder()
                .traceIdHigh(traceIdHigh)
                .traceId(traceId)
                .sampled(sampled)
                .build())
            .build();
      }
      return TraceContextOrSamplingFlags.create(TraceContext.newBuilder()
          .traceIdHigh(traceIdHigh)
          .traceId(traceId)
          .spanId(parent)
          .sampled(sampled)
          .extra(extra)
          .build());
    }
  }

  /** When present, this context was created with AWSPropagation */
  static final Extra MARKER = new Extra();
  static final List<Object> DEFAULT_EXTRA = Collections.singletonList(MARKER);

  static final class Extra { // hidden intentionally
    CharSequence fields;
  }
}
