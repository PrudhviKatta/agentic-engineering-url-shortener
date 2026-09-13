package com.schwab.urlshortener.api;

import java.util.Map;
import javax.sql.DataSource;
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
    public Map<String, String> readyz() throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.isValid(2);
        }
        return Map.of("status", "ready");
    }
}
