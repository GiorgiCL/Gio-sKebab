# PostHog Logs for production

The application keeps its normal console logs. `SafeFailureLogs` also emits a small set of fixed, structured WARN/ERROR records through the stable OpenTelemetry Java Logs Bridge API. Without an OpenTelemetry agent, or while `POSTHOG_LOGS_ENABLED` is unset/false, that path is a no-op. It never sends a network request during tests or normal local runs.

## Enable in production

1. Create a PostHog **EU Cloud** project and enable Logs. Copy its **project token** from Project settings. This is the same token used by the browser; it is not a personal API key. Treat the backend environment value as a deployment secret to keep operational configuration out of source control.
2. Download a pinned, stable [OpenTelemetry Java agent](https://opentelemetry.io/docs/zero-code/java/agent/) JAR as part of the deployment image and launch Java with `-javaagent:/path/to/opentelemetry-javaagent.jar`. Do not make the agent mandatory for local startup.
3. Set the following deployment environment variables:

   ```text
   POSTHOG_LOGS_ENABLED=true
   OTEL_SERVICE_NAME=gios-kebab-backend
   OTEL_RESOURCE_ATTRIBUTES=deployment.environment.name=production
   OTEL_TRACES_EXPORTER=none
   OTEL_METRICS_EXPORTER=none
   OTEL_LOGS_EXPORTER=otlp
   OTEL_EXPORTER_OTLP_LOGS_PROTOCOL=http/protobuf
   OTEL_EXPORTER_OTLP_LOGS_ENDPOINT=https://eu.i.posthog.com/i/v1/logs
   OTEL_EXPORTER_OTLP_LOGS_HEADERS=Authorization=Bearer%20<eu-project-token>
   OTEL_INSTRUMENTATION_LOGBACK_APPENDER_ENABLED=false
   ```

The last setting prevents automatic export of arbitrary framework/console log text. Only the explicitly allowlisted safe records go to PostHog. Keep other automatic log instrumentation disabled if added in future. The OTLP exporter batches asynchronously; failed delivery must not stop the application. Do not include headers, bodies, URLs with queries, or exception messages in the exported record. The app currently emits request outcome records for unexpected 5xx errors and sanitized Cloudinary failure records. Normal validation, 404, and unconfigured opening-hours responses are excluded.

OpenTelemetry's Java agent sets the global SDK used by `SafeFailureLogs`; with no agent, `GlobalOpenTelemetry.getOrNoop()` is inert. The app does not use the PostHog JVM SDK because duplicating each failure as both an exception event and a searchable log would add cost and implementation work without improving launch visibility. Frontend exceptions use PostHog Error Tracking separately.

For a controlled smoke test, enable the variables only in a throwaway environment, trigger one safe 5xx, then search PostHog Logs for `service.name = gios-kebab-backend` and `Backend request failed`. Never use live credentials in automated tests.
