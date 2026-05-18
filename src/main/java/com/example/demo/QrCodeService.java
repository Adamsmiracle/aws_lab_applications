package com.example.demo;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class QrCodeService {

    private final QrCodeRepository repository;

    public QrCodeService(QrCodeRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public QrCode generate(String content, String label, int size) {
        int clampedSize = Math.max(100, Math.min(1000, size));
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(
                content,
                BarcodeFormat.QR_CODE,
                clampedSize,
                clampedSize,
                Map.of(EncodeHintType.MARGIN, 1)
            );
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            QrCode qr = new QrCode();
            qr.setContent(content);
            qr.setLabel(label != null && !label.isBlank() ? label.trim() : null);
            qr.setSize(clampedSize);
            qr.setImageBase64(base64);
            return repository.save(qr);
        } catch (WriterException | IOException e) {
            throw new RuntimeException("QR generation failed: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<QrCode> findAll() {
        return repository.findAllByOrderByCreatedAtDesc()
            .stream()
            .limit(20)
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<QrCode> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public byte[] getImageBytes(Long id) {
        return repository.findById(id)
            .map(qr -> Base64.getDecoder().decode(qr.getImageBase64()))
            .orElseThrow(() -> new RuntimeException("QR code not found: " + id));
    }
}
