package com.kebabshop.backend.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;

/** Fixed, allowlisted failure details only. The OpenTelemetry global is a no-op without an agent. */
public final class SafeFailureLogs {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getenv("POSTHOG_LOGS_ENABLED"))
            && present("OTEL_EXPORTER_OTLP_LOGS_ENDPOINT") && present("OTEL_EXPORTER_OTLP_LOGS_HEADERS");

    private SafeFailureLogs() {}

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }

    public static void requestFailure(String method, String route, int status, String exceptionClass) {
        if (!ENABLED) return;
        try {
            GlobalOpenTelemetry.getOrNoop().getLogsBridge().get(SafeFailureLogs.class.getName())
                    .logRecordBuilder().setSeverity(Severity.ERROR).setSeverityText("ERROR")
                    .setBody("Backend request failed")
                    .setAttribute(AttributeKey.stringKey("http.request.method"), method)
                    .setAttribute(AttributeKey.stringKey("http.route"), route)
                    .setAttribute(AttributeKey.longKey("http.response.status_code"), (long) status)
                    .setAttribute(AttributeKey.stringKey("exception.type"), exceptionClass)
                    .emit();
        } catch (RuntimeException ignored) { /* Observability must not affect requests. */ }
    }

    public static void storageFailure(String operation, String kind, String failure, String exceptionClass) {
        if (!ENABLED) return;
        try {
            GlobalOpenTelemetry.getOrNoop().getLogsBridge().get(SafeFailureLogs.class.getName())
                    .logRecordBuilder().setSeverity(Severity.WARN).setSeverityText("WARN")
                    .setBody("Cloudinary image operation failed")
                    .setAttribute(AttributeKey.stringKey("storage.operation"), operation)
                    .setAttribute(AttributeKey.stringKey("storage.kind"), kind)
                    .setAttribute(AttributeKey.stringKey("failure.category"), failure)
                    .setAttribute(AttributeKey.stringKey("exception.type"), exceptionClass)
                    .emit();
        } catch (RuntimeException ignored) { /* Observability must not affect storage handling. */ }
    }
}
