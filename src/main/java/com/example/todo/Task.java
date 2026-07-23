package com.example.todo;

import java.time.OffsetDateTime;

/** A single to-do item, persisted in PostgreSQL (via RDS Proxy). */
public record Task(long id, String title, boolean completed, OffsetDateTime createdAt) {
}

