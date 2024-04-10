/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.messaging;

import brave.sampler.Matcher;
import brave.sampler.Matchers;

/**
 * Null safe matchers for use in {@link MessagingRuleSampler}.
 *
 * @see Matchers
 * @since 5.9
 */
public final class MessagingRequestMatchers {

  /**
   * Matcher for case-sensitive messaging operation names, such as "send" or "receive".
   *
   * @see MessagingRequest#operation()
   * @since 5.9
   */
  public static <Req extends MessagingRequest> Matcher<Req> operationEquals(String operation) {
    if (operation == null) throw new NullPointerException("operation == null");
    if (operation.isEmpty()) throw new NullPointerException("operation is empty");
    return new MessagingOperationEquals<Req>(operation);
  }

  static final class MessagingOperationEquals<Req extends MessagingRequest>
    implements Matcher<Req> {
    final String operation;

    MessagingOperationEquals(String operation) {
      this.operation = operation;
    }

    @Override public boolean matches(Req request) {
      return operation.equals(request.operation());
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof MessagingOperationEquals)) return false;
      MessagingOperationEquals that = (MessagingOperationEquals) o;
      return operation.equals(that.operation);
    }

    @Override public int hashCode() {
      return operation.hashCode();
    }

    @Override public String toString() {
      return "MessagingOperationEquals(" + operation + ")";
    }
  }

  /**
   * Matcher for case-sensitive message channel kinds, such as "queue" or "topic".
   *
   * @see MessagingRequest#channelKind()
   * @since 5.9
   */
  public static <Req extends MessagingRequest> Matcher<Req> channelKindEquals(String channelKind) {
    if (channelKind == null) throw new NullPointerException("channelKind == null");
    if (channelKind.isEmpty()) throw new NullPointerException("channelKind is empty");
    return new MessagingChannelKindEquals<Req>(channelKind);
  }

  static final class MessagingChannelKindEquals<Req extends MessagingRequest>
    implements Matcher<Req> {
    final String channelKind;

    MessagingChannelKindEquals(String channelKind) {
      this.channelKind = channelKind;
    }

    @Override public boolean matches(Req request) {
      return channelKind.equals(request.channelKind());
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof MessagingChannelKindEquals)) return false;
      MessagingChannelKindEquals that = (MessagingChannelKindEquals) o;
      return channelKind.equals(that.channelKind);
    }

    @Override public int hashCode() {
      return channelKind.hashCode();
    }

    @Override public String toString() {
      return "MessagingChannelKindEquals(" + channelKind + ")";
    }
  }

  /**
   * Matcher for case-sensitive message channel names, such as "hooks" or "complaints"
   *
   * @see MessagingRequest#channelName()
   * @since 5.9
   */
  public static <Req extends MessagingRequest> Matcher<Req> channelNameEquals(String channelName) {
    if (channelName == null) throw new NullPointerException("channelName == null");
    if (channelName.isEmpty()) throw new NullPointerException("channelName is empty");
    return new MessagingChannelNameEquals<Req>(channelName);
  }

  static final class MessagingChannelNameEquals<Req extends MessagingRequest>
    implements Matcher<Req> {
    final String channelName;

    MessagingChannelNameEquals(String channelName) {
      this.channelName = channelName;
    }

    @Override public boolean matches(Req request) {
      return channelName.equals(request.channelName());
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof MessagingChannelNameEquals)) return false;
      MessagingChannelNameEquals that = (MessagingChannelNameEquals) o;
      return channelName.equals(that.channelName);
    }

    @Override public int hashCode() {
      return channelName.hashCode();
    }

    @Override public String toString() {
      return "MessagingChannelNameEquals(" + channelName + ")";
    }
  }
}
