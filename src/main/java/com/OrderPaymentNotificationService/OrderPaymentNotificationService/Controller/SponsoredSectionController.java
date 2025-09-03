package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.SponsoredSection;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.SponsoredSectionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/sponsorships")
@RequiredArgsConstructor
public class SponsoredSectionController {

    private final SponsoredSectionService service;

    @PostMapping
    public ResponseEntity<?> createSponsorship(@RequestBody SponsoredSection request) {
        SponsoredSection created = service.createSponsorship(request);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/active")
    public ResponseEntity<List<SponsoredSection>> getActiveSponsorships() {
        return ResponseEntity.ok(service.getActiveSponsorships());
    }
}
