package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.SponsoredSlot;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.SponsoredSlotService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/sponsored-slots")
@RequiredArgsConstructor
public class SponsoredSlotController {

    private final SponsoredSlotService service;

    // Admin create slot
    @PostMapping
    public SponsoredSlot createSlot(@RequestBody SponsoredSlot slot) {
        return service.createSlot(slot);
    }

    // Admin update slot
    @PutMapping("/{id}")
    public SponsoredSlot updateSlot(@PathVariable UUID id, @RequestBody SponsoredSlot updated) {
        return service.updateSlot(id, updated);
    }

    // Get slot by ID
    @GetMapping("/{id}")
    public Optional<SponsoredSlot> getSlot(@PathVariable UUID id) {
        return service.getSlot(id);
    }

    // Get all active slots
    @GetMapping("/active")
    public List<SponsoredSlot> getAllActiveSlots() {
        return service.getAllActiveSlots();
    }

    // Shop/Brand books slot
    @PostMapping("/{id}/book")
    public SponsoredSlot bookSlot(
            @PathVariable UUID id,
            @RequestParam String shopId,
            @RequestParam String entityName,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String brandId) {
        return service.bookSlot(id, shopId, entityName, productId, brandId);
    }
}