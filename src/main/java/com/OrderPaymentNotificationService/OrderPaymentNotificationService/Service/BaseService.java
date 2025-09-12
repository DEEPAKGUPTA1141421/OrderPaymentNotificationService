package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BaseService {
    @Autowired
    private HttpServletRequest request;

    public String getPhone() {
        return (String) this.request.getAttribute("phone");
    }

    public String getRole() {
        return (String) this.request.getAttribute("role");
    }

    public UUID getId() {
        Object idAttr = this.request.getAttribute("id");
        if (idAttr == null) {
            return null;
        }
        return UUID.fromString(idAttr.toString());
    }
}
