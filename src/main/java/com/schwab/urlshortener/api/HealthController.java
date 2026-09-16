package com.schwab.urlshortener.api;

import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "ok");
    }

    @GetMapping("/readyz")
    public ResponseEntity<Map<String, String>> readyz() {
        try (var connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                return notReady();
            }
            return ResponseEntity.ok(Map.of("status", "ready"));
        } catch (SQLException ex) {
            return notReady();
        }
    }

    private ResponseEntity<Map<String, String>> notReady() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "not_ready"));
    }
}
