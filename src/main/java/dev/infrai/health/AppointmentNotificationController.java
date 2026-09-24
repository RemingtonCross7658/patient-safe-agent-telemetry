package dev.infrai.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/appointments")
final class AppointmentNotificationController {
    private final AppointmentNotificationService service;

    AppointmentNotificationController(AppointmentNotificationService service) {
        this.service = service;
    }

    @PostMapping("/notification")
    AppointmentNotificationService.Result prepare(@RequestBody AppointmentNotificationService.Command command) {
        return service.prepare(command);
    }

    @ExceptionHandler(AppointmentNotificationService.UnsafeNotificationException.class)
    ResponseEntity<Map<String, String>> unsafe(AppointmentNotificationService.UnsafeNotificationException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("status", "HELD_FOR_REVIEW", "reason", failure.getMessage()));
    }

    @ExceptionHandler(InfraiTelemetryClient.InfraiRejectedException.class)
    ResponseEntity<Map<String, String>> rejected(InfraiTelemetryClient.InfraiRejectedException failure) {
        HttpStatus status = HttpStatus.resolve(failure.statusCode());
        return ResponseEntity.status(status == null ? HttpStatus.BAD_REQUEST : status)
                .body(Map.of("status", "REJECTED", "code", failure.code(), "reason", failure.getMessage()));
    }
}
