package com.example.demo;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/qr")
public class QrApiController {

    private final QrCodeService service;

    public QrApiController(QrCodeService service) {
        this.service = service;
    }

    @GetMapping("/generate")
    public ResponseEntity<?> generate(
            @RequestParam String content,
            @RequestParam(defaultValue = "300") int size) {

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content cannot be empty"));
        }
        try {
            QrCode qr = service.generate(content.trim(), null, size);
            return ResponseEntity.ok(Map.of(
                "id", qr.getId(),
                "content", qr.getContent(),
                "imageBase64", qr.getImageBase64(),
                "createdAt", qr.getCreatedAt().toString()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "QR generation failed: " + e.getMessage()));
        }
    }


    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        return service.findById(id)
            .map(qr -> ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(Base64.getDecoder().decode(qr.getImageBase64())))
            .orElse(ResponseEntity.notFound().build());
    }
}
