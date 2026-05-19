// package com.example.demo;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;

// import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
// import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
// import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

// import java.util.HashMap;
// import java.util.Map;
// import java.util.UUID;

// @Service
// public class UserService {

//     @Autowired
//     private DynamoDbClient dynamoDbClient;


//     public void saveUser(String name, String email){
//         Map<String, AttributeValue> item = new HashMap<>();
//         item.put("userId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
//         item.put("name", AttributeValue.builder().s(name).build());
//         item.put("email", AttributeValue.builder().s(email).build());

//         String tableName = System.getenv("DYNAMODB_TABLE");
//         PutItemRequest request = PutItemRequest.builder()
//                 .tableName(tableName)
//                 .item(item)
//                 .build();

//         dynamoDbClient.putItem(request);
//     }

// }
