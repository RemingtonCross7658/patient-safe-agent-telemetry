#!/usr/bin/env sh
set -eu

curl --fail-with-body --request POST http://localhost:8080/appointments/notification \
  --header 'Content-Type: application/json' \
  --data '{"appointmentId":"APT-104","previousTime":"2026-09-20T09:00:00Z","newTime":"2026-09-20T15:30:00Z","reason":"clinician schedule change"}'
