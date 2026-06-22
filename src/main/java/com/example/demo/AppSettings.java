package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads the app's runtime config from the SSM "config plane" that the
 * infrastructure publishes under /{PROJECT_NAME}/*, then resolves the RDS
 * master credentials from the Secrets Manager secret referenced there.
 * Nothing about the environment is hard-coded in the image.
 */
@Component
public class AppSettings {

    private String imageBucket;
    private String cloudFrontDomain;
    private String dbEndpoint;
    private String dbName;
    private String dbUsername;
    private String dbPassword;

    @PostConstruct
    void load() throws Exception {
        String project = System.getenv().getOrDefault("PROJECT_NAME", "photo-uploader");
        String prefix = "/" + project + "/";

        Map<String, String> params = new HashMap<>();
        try (SsmClient ssm = SsmClient.create()) {
            String token = null;
            do {
                GetParametersByPathRequest.Builder req = GetParametersByPathRequest.builder()
                        .path(prefix).recursive(true).withDecryption(true);
                if (token != null) {
                    req.nextToken(token);
                }
                var resp = ssm.getParametersByPath(req.build());
                for (Parameter p : resp.parameters()) {
                    params.put(p.name(), p.value());
                }
                token = resp.nextToken();
            } while (token != null);
        }

        this.imageBucket = require(params, prefix + "image-bucket");
        this.cloudFrontDomain = require(params, prefix + "cloudfront-domain");
        this.dbEndpoint = require(params, prefix + "db-endpoint");
        this.dbName = require(params, prefix + "db-name");
        String dbSecretArn = require(params, prefix + "db-secret-arn");

        try (SecretsManagerClient sm = SecretsManagerClient.create()) {
            String secretJson = sm.getSecretValue(
                    GetSecretValueRequest.builder().secretId(dbSecretArn).build()).secretString();
            JsonNode node = new ObjectMapper().readTree(secretJson);
            this.dbUsername = node.get("username").asText();
            this.dbPassword = node.get("password").asText();
        }
    }

    private static String require(Map<String, String> params, String key) {
        String v = params.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing SSM parameter: " + key);
        }
        return v;
    }

    public String getImageBucket()      { return imageBucket; }
    public String getCloudFrontDomain() { return cloudFrontDomain; }
    public String getDbEndpoint()       { return dbEndpoint; }
    public String getDbName()           { return dbName; }
    public String getDbUsername()       { return dbUsername; }
    public String getDbPassword()       { return dbPassword; }
}
