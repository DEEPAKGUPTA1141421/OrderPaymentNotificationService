package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.invoice;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Generates human-readable, gapless-per-seller invoice numbers:
 * INV-{year}-{seq:06d}, e.g. INV-2026-000124.
 *
 * The actual increment runs in InvoiceCounterIncrementer under a pessimistic
 * row lock in its own REQUIRES_NEW transaction, so two sellers finalizing at
 * once never block each other and two concurrent finalizes for the *same*
 * seller serialize instead of racing — once the counter row exists. The very
 * first invoice of a seller's year has no row to lock yet, so two concurrent
 * "first ever" finalizes could both try to insert it; the unique
 * (seller_id, year) constraint lets only one succeed and we retry the loser
 * in a fresh transaction, where the row now exists to lock normally.
 */
@Service
@RequiredArgsConstructor
public class InvoiceNumberService {

    private final InvoiceCounterIncrementer incrementer;

    public String next(UUID sellerId) {
        try {
            return incrementer.increment(sellerId);
        } catch (DataIntegrityViolationException e) {
            return incrementer.increment(sellerId);
        }
    }
}
