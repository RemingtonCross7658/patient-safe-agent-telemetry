package dev.infrai.health;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
final class PatientSafetyPolicy {
    private static final List<String> CLINICAL_INSTRUCTIONS = List.of(
            "diagnosis", "diagnose", "change your dose", "stop taking", "start taking");

    Decision review(String draft) {
        String normalized = draft.toLowerCase(Locale.ROOT);
        boolean clinicalInstruction = CLINICAL_INSTRUCTIONS.stream().anyMatch(normalized::contains);
        if (clinicalInstruction) {
            return new Decision(false, "Draft crossed the operational-only boundary");
        }
        return new Decision(true, draft.strip());
    }

    record Decision(boolean send, String notification) { }
}
