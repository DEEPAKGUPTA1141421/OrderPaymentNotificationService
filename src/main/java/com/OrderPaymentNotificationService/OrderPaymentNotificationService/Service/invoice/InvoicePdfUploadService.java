package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.invoice;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

/**
 * Uploads a finalized invoice's rendered PDF to Cloudinary and serves it back
 * on demand. PDFs aren't images, so resource_type must be "raw".
 *
 * Cloudinary blocks unauthenticated CDN delivery of raw PDF/ZIP files by
 * default (account-wide security setting) — a plain delivery URL for a raw
 * PDF returns 401 to any caller, including this backend and the seller app.
 * Rather than relying on a CDN-signed URL (which only works if it's signed
 * for the exact access "type" the resource was originally uploaded with —
 * fragile across older invoices uploaded before this existed), downloads go
 * through Cloudinary's Admin "generate download URL" API instead: a
 * server-signed request (API key/secret, not a public CDN token) that
 * Cloudinary honors regardless of the resource's access type or the
 * account's raw-delivery restriction. overwrite=true on upload keeps a
 * retried finalize (same invoiceId) from accumulating duplicate uploads.
 */
@Service
public class InvoicePdfUploadService {

    @Autowired
    private Cloudinary cloudinary;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @SuppressWarnings("unchecked")
    public String upload(UUID invoiceId, byte[] pdfBytes) throws IOException {
        Map<String, Object> options = ObjectUtils.asMap(
                "resource_type", "raw",
                "folder", "invoices",
                // For resource_type=raw, Cloudinary serves the file at exactly
                // ".../<public_id>" with no automatic extension — omitting it
                // here is why the downloaded file had no .pdf suffix and
                // wouldn't open. The extension must be part of the public_id.
                "public_id", invoiceId + ".pdf",
                "overwrite", true
        );
        Map<String, Object> result = cloudinary.uploader().upload(pdfBytes, options);
        Object secureUrl = result.get("secure_url");
        return secureUrl != null ? secureUrl.toString() : result.get("url").toString();
    }

    /** Fetches the PDF bytes server-side via a freshly signed Admin download URL. */
    public byte[] downloadBytes(UUID invoiceId) throws Exception {
        Map<String, Object> options = ObjectUtils.asMap(
                "resource_type", "raw",
                "type", "upload",
                "attachment", true
        );
        String downloadUrl = cloudinary.privateDownload(publicId(invoiceId), "pdf", options);

        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Cloudinary returned " + response.statusCode() + " for invoice PDF " + invoiceId);
        }
        return response.body();
    }

    private String publicId(UUID invoiceId) {
        return "invoices/" + invoiceId + ".pdf";
    }
}
