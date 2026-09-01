package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.invoice;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoiceCounter;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.InvoiceCounterRepository;

import lombok.RequiredArgsConstructor;

/**
 * Split out of InvoiceNumberService so its REQUIRES_NEW transaction actually
 * goes through the Spring proxy — InvoiceNumberService.next() retries by
 * calling this as a separate bean, not via self-invocation (which would
 * silently skip @Transactional and defeat the pessimistic lock entirely).
 */
@Service
@RequiredArgsConstructor
class InvoiceCounterIncrementer {

    private final InvoiceCounterRepository counterRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String increment(UUID sellerId) {
        int year = ZonedDateTime.now(ZoneOffset.UTC).getYear();

        InvoiceCounter counter = counterRepository.findForUpdate(sellerId, year).orElseGet(() -> {
            InvoiceCounter c = new InvoiceCounter();
            c.setSellerId(sellerId);
            c.setYear(year);
            c.setLastValue(0);
            return c;
        });

        counter.setLastValue(counter.getLastValue() + 1);
        counterRepository.saveAndFlush(counter);

        return "INV-" + year + "-" + String.format("%06d", counter.getLastValue());
    }
}
