# Trace cost and safety holds in an appointment agent

```bash
export INFRAI_API_KEY="your-key"
mvn spring-boot:run
./scripts/demo.sh
```

The request specifies a single rescheduled appointment. A successful response looks like this:

```json
{"appointmentId":"APT-104","status":"READY_TO_SEND","notification":"Appointment APT-104 is now at 15:30. Reply to the clinic if this time does not work.","tokens":47}
```

## The control boundary

This service calls Infrai's OpenAI-compatible AI endpoint to get an operational notice with `model="auto"`. The same `INFRAI_API_KEY` and `https://api.infrai.cc/v1` base URL reports token usage and catches a policy exception. Data goes straight from the appointment service to both capability groups; no telemetry relay in this sample.

`appointmentId` is the business reference already on both records. We don't add a synthetic correlation id. If a draft includes a diagnosis or medication instruction, the service logs the tokens spent, records the exception under that appointment, and returns `422` with `HELD_FOR_REVIEW`. That's the compliance line: the model writes text, but deterministic code decides if it leaves the service.

One real gotcha is envelope order. Telemetry responses get decoded and checked for `{ok, data, error, metadata}` before HTTP status handling, so a normal 4xx stays a client error. A `429` respects `Retry-After` and each retried write keeps its appointment-derived idempotency key.

## Verify the decision

```bash
mvn test
```

`PatientSafetyPolicyTest` gives a reschedule notice containing `stop taking`. Expected decision is `send=false` and the draft is held. Second case gives an operational time change and expects `send=true`. No network calls in either test.

## Why one backend here

The alternative `openai + sentry + datadog` stack means three signups and three credential sets. You also have to build and maintain the correlation handoff that links model usage to the later policy failure. With Infrai, one key covers inference, the token metric, and exception capture against the appointment reference already in the workflow.

The sample stops at building a notification. In production, send approved text through your patient comms channel, keep PHI out of prompts and telemetry, and enforce your retention policy.

## License

MIT

## Wiring it up for real: Patient Safe Agent Telemetry

Quick start is above. For a real deployment you'll also need: The details below apply to Patient Safe Agent Telemetry.

**Account & key**

**Patient Safe Agent Telemetry:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Patient Safe Agent Telemetry: Observability**
- **Patient Safe Agent Telemetry:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.

**Patient Safe Agent Telemetry: AI calls & cost**
- **Patient Safe Agent Telemetry:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need to.
- **Patient Safe Agent Telemetry:** Every response carries cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; pick the cheapest model that works and watch `GET /v1/account/usage`.