package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML email templates used by EmailNotificationService.
 */
public final class EmailTemplates {

    private static final Pattern OTP_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    private EmailTemplates() {
    }

    /**
     * Wraps a plain-text notification body in a branded HTML shell. If the body
     * contains a 6-digit OTP code, it is pulled out and rendered as a highlighted
     * code block; otherwise the message is shown as-is.
     */
    public static String otpEmail(String subject, String message) {
        Matcher matcher = OTP_PATTERN.matcher(message == null ? "" : message);
        String otpBlock = "";
        if (matcher.find()) {
            String code = matcher.group(1);
            otpBlock = "<div style=\"margin:24px 0;text-align:center;\">"
                    + "<span style=\"display:inline-block;padding:14px 28px;font-size:28px;"
                    + "letter-spacing:8px;font-weight:700;color:#111827;background:#f3f4f6;"
                    + "border-radius:8px;\">" + escapeHtml(code) + "</span></div>";
        }

        return "<!doctype html>"
                + "<html><body style=\"margin:0;padding:0;background:#f9fafb;"
                + "font-family:Segoe UI,Roboto,Arial,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:#ffffff;border-radius:12px;overflow:hidden;"
                + "box-shadow:0 1px 3px rgba(0,0,0,0.08);\">"
                + "<tr><td style=\"background:#111827;padding:20px 32px;\">"
                + "<span style=\"color:#ffffff;font-size:18px;font-weight:700;\">Dashly</span></td></tr>"
                + "<tr><td style=\"padding:32px;\">"
                + "<h2 style=\"margin:0 0 12px;font-size:20px;color:#111827;\">" + escapeHtml(subject) + "</h2>"
                + "<p style=\"margin:0;font-size:15px;line-height:1.6;color:#374151;\">"
                + escapeHtml(message == null ? "" : message) + "</p>"
                + otpBlock
                + "<p style=\"margin:24px 0 0;font-size:13px;color:#9ca3af;\">"
                + "If you did not request this, you can safely ignore this email.</p>"
                + "</td></tr>"
                + "<tr><td style=\"padding:16px 32px;background:#f9fafb;\">"
                + "<span style=\"font-size:12px;color:#9ca3af;\">&copy; Dashly. All rights reserved.</span>"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
