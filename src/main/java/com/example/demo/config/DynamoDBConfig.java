// package com.example.demo.config;

// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import software.amazon.awssdk.regions.Region;
// import software.amazon.awssdk.regions.Region;
// import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

// @Configuration
// public class DynamoDBConfig {

//     @Bean
//     public DynamoDbClient dynamoDbClient() {

//         String region = System.getenv("AWS_REGION");
//         return DynamoDbClient.builder()
//                 .region(Region.of(region))
//                 .build();
//     }

// }
