package io.opentelemetry.javaagent.instrumentation.apachecamel.opentelemetry;/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.apachecamel.tracing.ExtractAdapter;
import io.opentelemetry.javaagent.instrumentation.apachecamel.tracing.InjectAdapter;
import io.opentelemetry.javaagent.instrumentation.apachecamel.tracing.SpanAdapter;
import io.opentelemetry.javaagent.instrumentation.apachecamel.tracing.SpanDecorator;
import io.opentelemetry.javaagent.instrumentation.apachecamel.tracing.decorators.AbstractInternalSpanDecorator;
import io.opentelemetry.javaagent.instrumentation.apachecamel.opentelemetry.propagators.OpenTelemetryGetter;
import io.opentelemetry.javaagent.instrumentation.apachecamel.opentelemetry.propagators.OpenTelemetrySetter;
import java.util.Set;
import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedResource;

@ManagedResource(description = "OpenTelemetryTracer")
public class OpenTelemetryTracer extends
    io.opentelemetry.javaagent.instrumentation.apachecamel.tracing.Tracer {

  private Tracer tracer;
  private String instrumentationName = "camel";

  private static SpanKind mapToSpanKind(
      io.opentelemetry.javaagent.instrumentation.apachecamel.tracing.SpanKind kind) {
    switch (kind) {
      case SPAN_KIND_CLIENT:
        return SpanKind.CLIENT;
      case SPAN_KIND_SERVER:
        return SpanKind.SERVER;
      case CONSUMER:
        return SpanKind.CONSUMER;
      case PRODUCER:
        return SpanKind.PRODUCER;
    }
    return SpanKind.SERVER;
  }

  public Tracer getTracer() {
    return tracer;
  }

  public void setTracer(Tracer tracer) {
    this.tracer = tracer;
  }

  public void setInstrumentationName(String instrumentationName) {
    this.instrumentationName = instrumentationName;
  }

  @Override
  protected void initTracer() {
    if (tracer == null) {
      Set<Tracer> tracers = getCamelContext().getRegistry().findByType(Tracer.class);
      if (tracers.size() == 1) {
        tracer = tracers.iterator().next();
      }
    }

    if (tracer == null) {
      tracer = GlobalOpenTelemetry.get().getTracer(instrumentationName, "3.7");
    }

    if (tracer == null) {
      // No tracer is available, so setup NoopTracer
      tracer = OpenTelemetry.noop().getTracer(instrumentationName);
    }
  }

  @Override
  protected SpanAdapter startSendingEventSpan(
      String operationName, io.opentelemetry.javaagent.instrumentation.apachecamel.tracing.SpanKind kind,
      SpanAdapter parent) {
    Baggage baggage = null;
    SpanBuilder builder = tracer.spanBuilder(operationName).setSpanKind(mapToSpanKind(kind));
    if (parent != null) {
      OpenTelemetrySpanAdapter oTelSpanWrapper = (OpenTelemetrySpanAdapter) parent;
      Span parentSpan = oTelSpanWrapper.getOpenTelemetrySpan();
      baggage = oTelSpanWrapper.getBaggage();
      builder = builder.setParent(Context.current().with(parentSpan));
    }
    return new OpenTelemetrySpanAdapter(builder.startSpan(), baggage);
  }

  @Override
  protected SpanAdapter startExchangeBeginSpan(
      Exchange exchange, SpanDecorator sd, String operationName,
      io.opentelemetry.javaagent.instrumentation.apachecamel.tracing.SpanKind kind,
      SpanAdapter parent) {
    SpanBuilder builder = tracer.spanBuilder(operationName);
    Baggage baggage;
    if (parent != null) {
      OpenTelemetrySpanAdapter spanFromExchange = (OpenTelemetrySpanAdapter) parent;
      builder = builder.setParent(Context.current().with(spanFromExchange.getOpenTelemetrySpan()));
      baggage = spanFromExchange.getBaggage();
    } else {
      ExtractAdapter adapter = sd.getExtractAdapter(exchange.getIn().getHeaders(), encoding);
      Context ctx = GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator()
          .extract(Context.current(), adapter,
              new OpenTelemetryGetter(adapter));
      Span span = Span.fromContext(ctx);
      baggage = Baggage.fromContext(ctx);
      if (span != null && span.getSpanContext().isValid()) {
        builder.setParent(ctx).setSpanKind(mapToSpanKind(sd.getReceiverSpanKind()));
      } else if (!(sd instanceof AbstractInternalSpanDecorator)) {
        builder.setSpanKind(mapToSpanKind(sd.getReceiverSpanKind()));
      }
    }

    return new OpenTelemetrySpanAdapter(builder.startSpan(), baggage);
  }

  @Override
  protected void finishSpan(SpanAdapter span) {
    OpenTelemetrySpanAdapter openTracingSpanWrapper = (OpenTelemetrySpanAdapter) span;
    openTracingSpanWrapper.getOpenTelemetrySpan().end();
  }

  @Override
  protected void inject(SpanAdapter span, InjectAdapter adapter) {
    OpenTelemetrySpanAdapter spanFromExchange = (OpenTelemetrySpanAdapter) span;
    Span otelSpan = spanFromExchange.getOpenTelemetrySpan();
    Context ctx;
    if (spanFromExchange.getBaggage() != null) {
      ctx = Context.current().with(otelSpan).with(spanFromExchange.getBaggage());
    } else {
      ctx = Context.current().with(otelSpan);
    }
    GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator()
        .inject(ctx, adapter, new OpenTelemetrySetter());
  }

}
