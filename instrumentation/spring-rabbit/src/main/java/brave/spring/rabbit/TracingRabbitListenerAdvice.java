package brave.spring.rabbit;

import brave.Span;
import brave.Tracing;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;

public class TracingRabbitListenerAdvice implements MethodInterceptor {

    private final Tracing tracing;

    public TracingRabbitListenerAdvice(Tracing tracing) {
        this.tracing = tracing;
    }

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {

        Message message = (Message) methodInvocation.getArguments()[1];

        Span span = tracing.tracer().nextSpan();
        span.kind(Span.Kind.CONSUMER).start().finish();

        return methodInvocation.proceed();
    }
}
