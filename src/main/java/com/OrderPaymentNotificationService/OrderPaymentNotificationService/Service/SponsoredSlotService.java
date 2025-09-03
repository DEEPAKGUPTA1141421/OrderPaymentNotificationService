package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.SponsoredSlot;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.SponsoredSlotRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SponsoredSlotService {

    private final SponsoredSlotRepository repository;

    // Admin creates slot
    public SponsoredSlot createSlot(SponsoredSlot slot) {
        slot.setActive(true);
        slot.setStartDate(null);
        slot.setEndDate(null);
        slot.setBookedBy(null);
        return repository.save(slot);
    }

    // Admin updates slot (price, availability etc.)
    public SponsoredSlot updateSlot(UUID id, SponsoredSlot updated) {
        SponsoredSlot slot = repository.findById(id).orElseThrow();
        slot.setPrice(updated.getPrice());
        slot.setActive(updated.isActive());
        slot.setSlotNumber(updated.getSlotNumber());
        slot.setPage(updated.getPage());
        return repository.save(slot);
    }

    // Get single slot
    public Optional<SponsoredSlot> getSlot(UUID id) {
        return repository.findById(id);
    }

    // Get all active slots (not expired)
    public List<SponsoredSlot> getAllActiveSlots() {
        return repository.findByActiveTrueAndEndDateAfter(LocalDateTime.now());
    }

    // Book a slot by seller/shop
    public SponsoredSlot bookSlot(UUID slotId, String shopId, String entityName, UUID productId, String brandId) {
        SponsoredSlot slot = repository.findById(slotId).orElseThrow();

        if (!slot.isActive() || slot.getEndDate() != null && slot.getEndDate().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Slot already booked or unavailable");
        }

        slot.setBookedBy(shopId);
        slot.setBookedEntityName(entityName);
        slot.setProductId(productId);
        slot.setBrandId(brandId);
        slot.setStartDate(LocalDateTime.now());
        slot.setEndDate(LocalDateTime.now().plusDays(7)); // Fixed 7 days
        slot.setActive(false); // Not available anymore

        return repository.save(slot);
    }
}
