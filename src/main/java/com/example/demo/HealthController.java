package com.example.demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight liveness endpoint for the ECS container health check.
 * Intentionally does NO work (no DB, no S3) — it only confirms the web
 * server is up and serving, so DB hiccups don't cause ECS to kill the task.
 */
@RestController
public class HealthController {

    @GetMapping(value = "/healthz", produces = MediaType.TEXT_PLAIN_VALUE)
    public String health() {
        return "OK";
    }
}
