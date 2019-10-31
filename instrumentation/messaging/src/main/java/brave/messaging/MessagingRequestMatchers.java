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
    return new MessagingOperationEquals<>(operation);
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
    return new MessagingChannelKindEquals<>(channelKind);
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
    return new MessagingChannelNameEquals<>(channelName);
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
