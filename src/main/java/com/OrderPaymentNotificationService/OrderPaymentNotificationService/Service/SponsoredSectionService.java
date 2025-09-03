package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.SponsoredSection;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.SponsoredSectionRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SponsoredSectionService {

    private final SponsoredSectionRepository repository;

    // Seller/Brand pays & creates sponsorship
    public SponsoredSection createSponsorship(SponsoredSection request) {
        // Normally: verify payment ✅, verify product/brand exists ✅
        request.setActive(true);
        return repository.save(request);
    }

    // Fetch all active sponsorships for frontend home page
    public List<SponsoredSection> getActiveSponsorships() {
        return repository.findActiveSponsoredSections();
    }

    // Expire sponsorships automatically
    @Transactional
    @Scheduled(cron = "0 0 0 * * *") // every midnight
    public void expireOldSponsorships() {
        List<SponsoredSection> sections = repository.findAll();
        sections.forEach(s -> {
            if (s.getEndDate().isBefore(LocalDate.now())) {
                s.setActive(false);
            }
        });
        repository.saveAll(sections);
    }
}
