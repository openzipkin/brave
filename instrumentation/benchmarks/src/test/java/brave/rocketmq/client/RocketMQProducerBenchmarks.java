/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rocketmq.client;

import brave.Tracing;
import brave.kafka.clients.TracingProducerBenchmarks;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import static org.apache.rocketmq.client.producer.SendStatus.SEND_OK;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class RocketMQProducerBenchmarks {
  Message message;
  DefaultMQProducer producer, tracingProducer;

  @Setup(Level.Trial) public void init() {
    message = new Message("zipkin", "zipkin".getBytes());
    Tracing tracing = Tracing.newBuilder().build();
    producer = new FakeProducer();
    tracingProducer = new FakeProducer();
    tracingProducer.getDefaultMQProducerImpl().registerSendMessageHook(
        new TracingSendMessage(RocketMQTracing.newBuilder(tracing).build())
    );
  }

  @TearDown(Level.Trial) public void close() {
    Tracing.current().close();
  }

  @Benchmark public SendResult send_baseCase() throws Exception {
    return producer.send(message);
  }

  @Benchmark public void send_traced() throws Exception {
    tracingProducer.send(message);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .addProfiler("gc")
        .include(".*" + TracingProducerBenchmarks.class.getSimpleName())
        .build();

    new Runner(opt).run();
  }

  static final class FakeProducer extends DefaultMQProducer {
    @Override public SendResult send(Message msg) {
      SendResult sendResult = new SendResult();
      sendResult.setMsgId("zipkin");
      sendResult.setSendStatus(SEND_OK);
      return sendResult;
    }
  }
}
