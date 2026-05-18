package com.example.demo;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Base64;

@Controller
@RequestMapping("/qr")
public class QrWebController {

    private final QrCodeService service;

    public QrWebController(QrCodeService service) {
        this.service = service;
    }

    @GetMapping
    public String generatePage(Model model) {
        if (model.containsAttribute("generatedId")) {
            Long id = (Long) model.getAttribute("generatedId");
            service.findById(id).ifPresent(qr -> model.addAttribute("generatedQr", qr));
        }
        return "qr/generate";
    }

    @PostMapping("/generate")
    public String generate(
            @RequestParam String content,
            @RequestParam(required = false) String label,
            @RequestParam(defaultValue = "300") int size,
            RedirectAttributes ra) {

        if (content == null || content.isBlank()) {
            ra.addFlashAttribute("error", "Content cannot be empty.");
            return "redirect:/qr";
        }
        try {
            QrCode qr = service.generate(content.trim(), label, size);
            ra.addFlashAttribute("generatedId", qr.getId());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Failed to generate QR code. Please check your input and try again.");
        }
        return "redirect:/qr";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("qrCodes", service.findAll());
        return "qr/history";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return service.findById(id)
            .map(qr -> { model.addAttribute("qr", qr); return "qr/detail"; })
            .orElseGet(() -> {
                ra.addFlashAttribute("error", "QR code not found.");
                return "redirect:/qr/history";
            });
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        return service.findById(id)
            .map(qr -> ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"qr-" + id + ".png\"")
                .body(Base64.getDecoder().decode(qr.getImageBase64())))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        service.delete(id);
        ra.addFlashAttribute("success", "QR code deleted.");
        return "redirect:/qr/history";
    }
}
