package dev.infrai.health;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.stereotype.Service;

@Service
final class AppointmentNotificationService {
    private final OpenAIClient ai;
    private final InfraiTelemetryClient telemetry;
    private final PatientSafetyPolicy safety;

    AppointmentNotificationService(OpenAIClient ai, InfraiTelemetryClient telemetry, PatientSafetyPolicy safety) {
        this.ai = ai;
        this.telemetry = telemetry;
        this.safety = safety;
    }

    Result prepare(Command command) {
        ChatCompletion completion = ai.chat().completions().create(ChatCompletionCreateParams.builder()
                .model("auto")
                .addSystemMessage("Write one operational appointment notice. Do not give diagnosis or medication advice.")
                .addUserMessage("Appointment " + command.appointmentId() + " moved from "
                        + command.previousTime() + " to " + command.newTime() + ". Reason: " + command.reason())
                .build());

        long tokens = completion.usage().map(usage -> usage.totalTokens()).orElse(0L);
        telemetry.reportTokenUsage(command.appointmentId(), tokens);
        String draft = completion.choices().getFirst().message().content().orElse("");
        PatientSafetyPolicy.Decision decision = safety.review(draft);
        if (!decision.send()) {
            UnsafeNotificationException failure = new UnsafeNotificationException(decision.notification());
            telemetry.capture(command.appointmentId(), failure);
            throw failure;
        }
        return new Result(command.appointmentId(), "READY_TO_SEND", decision.notification(), tokens);
    }

    record Command(String appointmentId, String previousTime, String newTime, String reason) { }
    record Result(String appointmentId, String status, String notification, long tokens) { }

    static final class UnsafeNotificationException extends RuntimeException {
        UnsafeNotificationException(String message) { super(message); }
    }
}
