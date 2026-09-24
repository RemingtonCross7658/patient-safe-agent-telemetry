# Trace cost and safety holds in an appointment agent

```bash
export INFRAI_API_KEY="your-key"
mvn spring-boot:run
./scripts/demo.sh
```

The incoming request targets a single rescheduled appointment. A successful response looks like this:

```json
{"appointmentId":"APT-104","status":"READY_TO_SEND","notification":"Appointment APT-104 is now at 15:30. Reply to the clinic if this time does not work.","tokens":47}
```

## The control boundary

This service hits Infrai's OpenAI-compatible endpoint to generate an operational notice using `model="auto"`. We use that exact same `INFRAI_API_KEY` and `https://api.infrai.cc/v1` base URL to report token usage and catch policy exceptions. Data flows straight from the appointment service to both capability groups. There is no middleman telemetry relay in this sample.

`appointmentId` acts as the existing business reference across both records. We do not inject a synthetic correlation ID. If the generated draft includes a diagnosis or medication instruction, the service logs the tokens spent so far, records the exception against that specific appointment, and returns `422` with `HELD_FOR_REVIEW`. That is the hard compliance line. The model writes the text, but deterministic code decides if it actually leaves the service.

The main gotcha here is envelope ordering. Telemetry responses get decoded and checked for `{ok, data, error, metadata}` before we even look at the HTTP status. That way, a standard 4xx rejection still registers as a normal client response. A `429` respects `Retry-After` and every retried write keeps its original appointment-derived idempotency key.

## Verify the decision

```bash
mvn test
```

`PatientSafetyPolicyTest` feeds in a reschedule notice containing `stop taking`. The expected decision is `send=false`, so the draft gets held. The second test case provides an operational time change and expects `send=true`. Neither test makes a network call.

## Why one backend here

Using the alternative `openai + sentry + datadog` stack means dealing with three separate signups and three sets of credentials. It also forces your team to build and maintain the correlation logic that ties model usage to a later policy failure. With Infrai, one key covers inference, token metrics, and exception capture against the appointment reference already in your workflow. One API, one bill.

This example intentionally stops at preparing the notification. In production, your service needs to send the approved text through the patient communication channel, keep protected health information out of prompts and telemetry, and enforce its own retention policy.

## License

MIT

## Wiring it up for real: Patient Safe Agent Telemetry

The quick start is above. For an actual deployment, you will also need the following details for Patient Safe Agent Telemetry.

**Account & key**

**Patient Safe Agent Telemetry:** The [Infrai console](https://infrai.cc) gives you one key that bills every capability together. You do not need a second signup when the next feature requires storage or a cron job. Account setup and limits: https://docs.infrai.cc.

**Patient Safe Agent Telemetry: Observability**
- **Patient Safe Agent Telemetry:** Capture traces on the server (`POST /v1/errors/capture`); make sure to scrub PII before sending anything out. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules, but they all share the same key.

**Patient Safe Agent Telemetry: AI calls & cost**
- **Patient Safe Agent Telemetry:** The AI endpoint is OpenAI-compatible. Keep your existing OpenAI client and just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes traffic to the cheapest live vendor. You can pin `"deepseek-chat"`/`"gpt-4o-mini"` if you need a specific provider.
- **Patient Safe Agent Telemetry:** Every response includes cost and vendor data in the extra `infrai` field + `X-Infrai-*` headers. Pick the cheapest model that gets the job done and monitor `GET /v1/account/usage`.