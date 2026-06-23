package com.example.demo;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

/** Uploads image bytes to the private S3 bucket. Objects are served to the
 *  browser only via CloudFront, never directly from S3. */
@Service
public class S3ImageService {

    private final S3Client s3 = S3Client.create();
    private final AppSettings settings;

    public S3ImageService(AppSettings settings) {
        this.settings = settings;
    }

    /** Stores the file under a random key and returns that key. */
    public String upload(MultipartFile file) throws IOException {
        String ext = "";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String key = "images/" + UUID.randomUUID() + ext;

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(settings.getImageBucket())
                        .key(key)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromBytes(file.getBytes()));

        return key;
    }

    /** Removes the object from the bucket. */
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(settings.getImageBucket())
                .key(key)
                .build());
    }
}
