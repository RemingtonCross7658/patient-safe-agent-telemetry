package dev.infrai.health;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientSafetyPolicyTest {
    private final PatientSafetyPolicy policy = new PatientSafetyPolicy();

    @Test
    void holdsClinicalInstructionInsteadOfSendingIt() {
        PatientSafetyPolicy.Decision decision = policy.review(
                "Your visit is now at 15:30. Stop taking the current medication before arrival.");

        assertThat(decision.send()).isFalse();
        assertThat(decision.notification()).isEqualTo("Draft crossed the operational-only boundary");
    }

    @Test
    void releasesOperationalRescheduleNotice() {
        PatientSafetyPolicy.Decision decision = policy.review(
                "Appointment APT-104 is now at 15:30. Reply to the clinic if this time does not work.");

        assertThat(decision.send()).isTrue();
    }
}
