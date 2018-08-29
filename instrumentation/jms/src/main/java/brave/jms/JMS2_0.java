package brave.jms;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicates a type or method is only available since JMS 2.0, mainly to be careful so we don't
 * break JMS 1.1.
 *
 * <p>For example, a wrapped method on a type present in JMS 1.1, but defined in JMS 2.0, should
 * not use {@linkplain Override}.
 */
@java.lang.annotation.Documented
@java.lang.annotation.Retention(RetentionPolicy.SOURCE)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD}) @interface JMS2_0 {
}