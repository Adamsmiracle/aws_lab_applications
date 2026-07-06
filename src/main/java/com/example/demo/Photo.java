package com.example.demo;

import java.time.OffsetDateTime;

/** A photo's metadata row (the image bytes live in S3, keyed by s3Key). */

public record Photo(long id, String s3Key, String description, OffsetDateTime createdAt) {
}
