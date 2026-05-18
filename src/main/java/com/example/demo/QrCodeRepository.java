package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QrCodeRepository extends JpaRepository<QrCode, Long> {
    List<QrCode> findAllByOrderByCreatedAtDesc();
}
