package com.example.demo;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "qr_codes")
public class QrCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 2000, nullable = false)
    private String content;

    @Column(length = 200)
    private String label;

    private int size = 300;

    private Instant createdAt;

    @Lob
    @Column(name = "image_base64", columnDefinition = "TEXT")
    private String imageBase64;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
}
