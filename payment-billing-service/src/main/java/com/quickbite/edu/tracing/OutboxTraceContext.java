package com.quickbite.edu.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.Map;

/**
 * Noi trace qua Outbox: luc ghi outbox (trong request) luu W3C traceparent cua span hien tai;
 * luc publish (job @Scheduled, thread khac) dat lai traceparent do lam parent de span Kafka producer
 * va consumer phia sau nam CUNG trace voi request ban dau.
 * Khong co OTel agent -> Span hien tai khong hop le -> capture() tra null, restore() la no-op.
 */
public final class OutboxTraceContext {

    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private OutboxTraceContext() {
    }

    /** traceparent cua span hien tai, hoac null neu khong co trace hop le. */
    public static String capture() {
        SpanContext sc = Span.current().getSpanContext();
        if (!sc.isValid()) {
            return null;
        }
        return "00-" + sc.getTraceId() + "-" + sc.getSpanId() + "-" + sc.getTraceFlags().asHex();
    }

    /** Dat traceparent da luu lam context hien tai; dong Scope sau khi gui xong. Luon tra ve Scope (khong null). */
    public static Scope restore(String traceParent) {
        if (traceParent == null || traceParent.isBlank()) {
            return Scope.noop();
        }
        try {
            Context ctx = W3CTraceContextPropagator.getInstance()
                    .extract(Context.current(), Map.of("traceparent", traceParent), GETTER);
            return ctx.makeCurrent();
        } catch (RuntimeException ex) {
            return Scope.noop();
        }
    }
}
