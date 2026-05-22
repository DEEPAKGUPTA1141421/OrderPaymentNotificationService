# Buy Now — Frontend Integration Guide

## Overview

The **Buy Now** flow lets a user purchase a single product directly from the product detail page, skipping the cart entirely.

```
Product Detail Page
       │
       │  User taps "Buy Now"
       ▼
[Step 1] POST /api/v1/buy-now          ← creates booking
       │
       │  bookingId + totalAmountPaise returned
       ▼
[Step 2] POST /api/v1/payment          ← initiates payment gateway
       │
       │  For PhonePe: token returned → redirect to PG
       │  For COD:     confirmed immediately
       ▼
[Step 3] Payment completed
       │
       │  PhonePe: GET /api/v1/payment/validate-payment
       │  COD:     user generates OTP → delivery partner confirms
       ▼
Order in CONFIRMED state
```

---

## Authentication

All Buy Now endpoints require a valid JWT in the `Authorization` header.

```
Authorization: Bearer <jwt_token>
```

The token must carry `ROLE_USER`. Delivery-partner and guest tokens will be rejected.

---

## Step 1 — Create Booking

### Request

```
POST /api/v1/buy-now
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "productId":        "550e8400-e29b-41d4-a716-446655440000",
  "variantId":        "660e8400-e29b-41d4-a716-446655440001",
  "quantity":         1,
  "deliveryAddressId":"770e8400-e29b-41d4-a716-446655440002"
}
```

| Field              | Type    | Required | Notes                                |
|--------------------|---------|----------|--------------------------------------|
| `productId`        | UUID    | Yes      | From product detail page             |
| `variantId`        | UUID    | Yes      | Selected variant (size, color, etc.) |
| `quantity`         | integer | Yes      | 1–10                                 |
| `deliveryAddressId`| UUID    | Yes      | One of the user's saved addresses    |

### Success Response — 201

```json
{
  "success": true,
  "message": "Booking created. Proceed to payment.",
  "statusCode": 201,
  "data": {
    "bookingId":         "aabbccdd-0000-0000-0000-000000000001",
    "shopId":            "aabbccdd-0000-0000-0000-000000000002",
    "productName":       "Blue Denim Jacket",
    "quantity":          1,
    "status":            "INITIATED",
    "expiresAt":         "2026-04-22T11:05:00Z",
    "totalAmountPaise":  "159900",
    "totalAmountRupees": 1599.00,
    "breakdown": {
      "unitPrice":     1499.00,
      "quantity":      1,
      "subTotal":      1499.00,
      "gst":             26.98,
      "delivery":        40.00,
      "serviceCharge":   33.02,
      "grandTotal":    1599.00
    }
  }
}
```

> **Important:** `expiresAt` is **5 minutes** from creation. If the user does not complete payment before this time, the booking expires and a new Buy Now request is required.

### Error Responses

| HTTP | `message`                                                        | Action                        |
|------|------------------------------------------------------------------|-------------------------------|
| 400  | `"Product ID is required"` / `"Quantity must be at least 1"`    | Fix request fields            |
| 401  | `"Unauthorized"`                                                 | Re-authenticate               |
| 403  | `"Access denied"`                                                | Token lacks ROLE_USER         |
| 409  | `"A purchase is already in progress for this product. Please wait."` | Show spinner; retry after 5s |
| 409  | `"'Jacket' is currently unavailable."`                          | Show out-of-stock message     |
| 409  | `"Only 2 unit(s) available for 'Jacket'."`                      | Reduce quantity               |
| 500  | `"Buy Now failed: ..."`                                          | Show generic error toast      |

---

## Step 2 — Initiate Payment

Use the `bookingId` and `totalAmountPaise` from Step 1.

### PhonePe (online payment)

```
POST /api/v1/payment
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "gateway":           "phonepe",
  "bookingId":         "aabbccdd-0000-0000-0000-000000000001",
  "idempotencyKey":    "buynow-<bookingId>-<timestamp>",
  "pgPaymentAmount":   "1599.00",
  "pgPayment":         true,
  "pointPayment":      false,
  "pointPaymentAmount": null
}
```

**Success response:**
```json
{
  "success": true,
  "data": {
    "orderId":       "PHONEPE_ORDER_XYZ",
    "token":         "<phonepe_checkout_token>",
    "transactionId": "uuid",
    "status":        "PENDING"
  }
}
```

Redirect the user to the PhonePe checkout using the returned `token`.

### Cash on Delivery (COD)

```json
{
  "gateway":           "cod",
  "bookingId":         "aabbccdd-0000-0000-0000-000000000001",
  "idempotencyKey":    "buynow-<bookingId>-<timestamp>",
  "pgPaymentAmount":   "0",
  "pgPayment":         false,
  "pointPayment":      false,
  "pointPaymentAmount": null
}
```

COD is confirmed immediately — booking moves to `CONFIRMED`. No redirect needed.

### Mixed Payment (PG + Loyalty Points)

```json
{
  "gateway":           "phonepe",
  "bookingId":         "aabbccdd-0000-0000-0000-000000000001",
  "idempotencyKey":    "buynow-<bookingId>-<timestamp>",
  "pgPaymentAmount":   "999.00",
  "pgPayment":         true,
  "pointPayment":      true,
  "pointPaymentAmount":"600.00"
}
```

---

## Step 3 — Verify Payment (PhonePe only)

After the user returns from the PhonePe checkout page:

```
GET /api/v1/payment/validate-payment
    ?merchantOrderId=<orderId from Step 2>
    &gateway=phonepe
Authorization: Bearer <token>
```

| Status in response | Meaning           | Action                      |
|--------------------|-------------------|-----------------------------|
| `SUCCESS`          | Payment received  | Navigate to order success   |
| `PENDING`          | Still processing  | Poll again in 3s (max 10x)  |
| `FAILED`           | Payment declined  | Show retry / choose COD     |

---

## COD Post-Delivery Flow (not needed for frontend at checkout)

When the delivery partner arrives:

1. **Customer** calls `POST /api/v1/payment/cod/generate-otp` with `{ "transactionId": "<uuid>" }` — receives a 6-digit OTP valid for 10 minutes.
2. Customer reads the OTP to the delivery partner.
3. **Delivery partner** calls `POST /api/v1/payment/cod/confirm` — transaction marked `SUCCESS`.

Alternatively, the delivery partner can generate a QR code via `POST /api/v1/payment/cod/generate-payment-qr` and the customer pays via UPI scan.

---

## Order Status Tracking

After payment, poll the order status:

```
GET /api/v1/order-tracking/<bookingId>
Authorization: Bearer <token>
```

State machine:
```
INITIATED → CONFIRMED → PROCESSING → OUT_FOR_DELIVERY → DELIVERED
          ↓           ↓             ↓
       CANCELLED   REVERSED     CANCELLED
```

---

## Idempotency Key

Construct as: `buynow-<bookingId>-<unix-timestamp-ms>`

This prevents duplicate payment orders if the user taps "Pay" twice.

---

## Amount Conversion Note

`totalAmountPaise` is in **paise** (1 INR = 100 paise).

Convert to rupees for display: `rupees = totalAmountPaise / 100`

When calling `POST /api/v1/payment`, send `pgPaymentAmount` as **rupees** (string, up to 2 decimal places), e.g. `"1599.00"`.

---

## Security Notes for Frontend

1. **Never store the JWT in localStorage** — use an HttpOnly cookie or in-memory store.
2. **Do not retry** `POST /api/v1/buy-now` on network error without explicit user action — each call creates a new booking.
3. **Respect `expiresAt`** — display a countdown timer and cancel the booking if the user abandons payment.
4. The `idempotencyKey` for `POST /api/v1/payment` **must be unique per payment attempt** to prevent duplicate charges.
5. All UUIDs are case-insensitive but should be passed in lowercase hyphenated form.

---

## Product Service Contract (backend-to-backend)

The Buy Now flow calls the following internal endpoint on the **Product Service**. The Product Service team must implement this:

```
GET /internal/v1/product/{productId}/variant/{variantId}
X-Internal-Api-Key: <shared secret>
```

Expected response body:
```json
{
  "success": true,
  "data": {
    "productId":       "uuid",
    "variantId":       "uuid",
    "shopId":          "uuid",
    "name":            "Blue Denim Jacket",
    "image":           "https://cdn.example.com/img.jpg",
    "description":     "...",
    "price":           1499.00,
    "gstRate":         1.8,
    "deliveryCharge":  40.00,
    "stockAvailable":  25,
    "available":       true
  }
}
```

- `price` — unit selling price in **rupees** (after any item-level discount; no cart coupon)
- `gstRate` — GST as a percentage (e.g. `18.0` for 18%)
- `deliveryCharge` — flat delivery charge for this item in rupees
- `available: false` → booking rejected with 409
- `stockAvailable: 0` → booking rejected with 409

---

## Quick Reference

| Step | Method | Endpoint                                  | Auth       |
|------|--------|-------------------------------------------|------------|
| 1    | POST   | `/api/v1/buy-now`                         | ROLE_USER  |
| 2    | POST   | `/api/v1/payment`                         | ROLE_USER  |
| 3    | GET    | `/api/v1/payment/validate-payment`        | ROLE_USER  |
| —    | GET    | `/api/v1/order-tracking/{bookingId}`      | ROLE_USER  |
| —    | GET    | `/api/v1/booking/{bookingId}`             | ROLE_USER  |
| —    | POST   | `/api/v1/payment/cod/generate-otp`        | ROLE_USER  |
| —    | POST   | `/api/v1/payment/cod/confirm`             | ROLE_DELIVERY |
