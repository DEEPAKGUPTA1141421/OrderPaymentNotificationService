package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a PhonePe checkout QR code for doorstep digital payment.
 *
 * ── How it works ────────────────────────────────────────────────────────────
 *  1. Delivery partner arrives and calls POST /api/v1/payment/cod/generate-payment-qr
 *  2. We create a PhonePe payment order using the COD transaction's UUID as
 *     the merchantOrderId.
 *  3. PhonePe returns a checkout token.  We build the payment page URL:
 *        https://mercury.phonepe.com/transact/v3?token={token}
 *  4. We generate a QR PNG from that URL using ZXing and return it as
 *     a base64 data URI.
 *  5. Customer scans QR with any camera app → opens PhonePe checkout page →
 *     pays with any UPI app.
 *  6. PhonePe fires a webhook → PhonePePaymentGateway.handleWebhook() finds
 *     the transaction by transcationNumber and marks it SUCCESS.
 *  7. Rider's app polls GET /api/v1/payment/cod/qr-status/{transactionId}
 *     to see when payment is confirmed.
 *
 * ── Money flow ───────────────────────────────────────────────────────────────
 *  Customer → PhonePe → Company's merchant account → reflected as
 *  Transaction.Status.SUCCESS in our DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QrPaymentService {

    private static final String QR_LOCK_PREFIX = "cod:qr:lock:";
    private static final int    QR_SIZE_PX     = 400;

    private final TransactionRepository txRepo;
    private final PaymentRepository     paymentRepo;
    private final PhonePeService        phonePeService;
    private final RedisLockService      redisLockService;

    @Value("${phonepe.checkout.base.url:https://mercury.phonepe.com/transact/v3}")
    private String checkoutBaseUrl;

    // ══════════════════════════════════════════════════════════════════════════
    //  Generate payment QR   (called by ROLE_DELIVERY)
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public Map<String, Object> generatePaymentQr(UUID transactionId, UUID deliveryBoyId) {

        // 1. Prevent duplicate QR generation (2-second lock)
        boolean locked = redisLockService.acquireLock(
                QR_LOCK_PREFIX, deliveryBoyId, transactionId, 1, 2);
        if (!locked) {
            throw new IllegalStateException("QR generation already in progress. Please retry.");
        }

        try {
            // 2. Load and validate the COD transaction
            Transaction tx = txRepo.findById(transactionId)
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

            if (tx.getMethod() != Transaction.Method.COD) {
                throw new IllegalStateException("QR can only be generated for COD transactions");
            }
            if (tx.getStatus() == Transaction.Status.SUCCESS) {
                throw new IllegalStateException("Payment already confirmed for this order.");
            }
            if (tx.getStatus() != Transaction.Status.PENDING) {
                throw new IllegalStateException(
                        "Cannot generate QR for transaction in state: " + tx.getStatus());
            }

            // 3. Convert paise → rupees for PhonePe (it expects rupees as string)
            BigDecimal amountRupees = new BigDecimal(tx.getAmount())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // 4. Create a PhonePe order.
            //    merchantOrderId = transaction's UUID (PhonePe will echo this back in webhook)
            String merchantOrderId  = tx.getTranscationNumber().toString();
            String idempotencyKey   = "qr-" + transactionId;

            Map<String, Object> phonePeResponse = phonePeService.createOrder(
                    merchantOrderId,
                    amountRupees.toPlainString(),
                    idempotencyKey
            );

            String token        = String.valueOf(phonePeResponse.get("token"));
            String phonePeOrderId = String.valueOf(phonePeResponse.get("orderId"));

            // 5. Build the payment page URL — customer scans this to pay
            String paymentUrl = checkoutBaseUrl + "?token=" + token;

            // 6. Store PhonePe's orderId in the transaction for webhook lookup
            tx.setOrderId(phonePeOrderId);
            txRepo.save(tx);

            // 7. Generate QR image (PNG → base64)
            String qrBase64 = buildQrBase64(paymentUrl, QR_SIZE_PX);

            log.info("Payment QR generated | transactionId={} merchantOrderId={} deliveryBoyId={} amountRupees={}",
                    transactionId, merchantOrderId, deliveryBoyId, amountRupees);

            return Map.of(
                    "qrImageBase64",    "data:image/png;base64," + qrBase64,
                    "paymentUrl",       paymentUrl,
                    "amountRupees",     amountRupees,
                    "amountPaise",      tx.getAmount(),
                    "merchantOrderId",  merchantOrderId,
                    "transactionId",    transactionId,
                    "expiresInMinutes", 10,
                    "instructions",     "Customer scans QR → pays via any UPI app → payment auto-confirmed."
            );

        } finally {
            redisLockService.releaseLock(QR_LOCK_PREFIX, deliveryBoyId, transactionId);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  QR status poll   (called by ROLE_DELIVERY app to check if paid)
    // ══════════════════════════════════════════════════════════════════════════

    public Map<String, Object> getQrPaymentStatus(UUID transactionId, UUID deliveryBoyId) {
        Transaction tx = txRepo.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (tx.getMethod() != Transaction.Method.COD) {
            throw new IllegalStateException("Not a COD transaction");
        }

        Payment payment = tx.getPayment();

        return Map.of(
                "transactionId",  transactionId,
                "status",         tx.getStatus().name(),
                "paymentStatus",  payment.getStatus().name(),
                "amountPaise",    tx.getAmount(),
                "paid",           tx.getStatus() == Transaction.Status.SUCCESS
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Encodes a URL/string as a QR code and returns a base64 PNG string.
     * Error correction level H (30% damage tolerance) for durability on screen.
     */
    private String buildQrBase64(String content, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (Exception e) {
            throw new RuntimeException("QR code generation failed: " + e.getMessage(), e);
        }
    }
}
