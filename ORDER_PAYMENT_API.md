# Order & Payment API — Frontend Integration Guide

> Service base URL: `https://<your-api-domain>`  
> All endpoints (except `/api/v1/payment/webhook/*`) require:
> ```
> Authorization: Bearer <jwt-token>
> Content-Type: application/json
> ```

---

## Amount convention

All monetary values in **paise** (₹1 = 100 paise).  
Divide by 100 to display in rupees.

```
"49900" → ₹499.00
"118784" → ₹1,187.84
```

---

## Roles

| Role | Who |
|---|---|
| `ROLE_USER` | Customer app |
| `ROLE_DELIVERY` | Delivery partner app |

---

---

# PART 1 — Order Creation Flow

---

## Step A — Create Bookings from Cart

Call this when the user taps **"Proceed to Checkout"** on the cart page.  
The backend reads the cart, validates stock, and creates one booking per shop.

```
GET /api/v1/booking/checkout
```

**Required by:** `ROLE_USER`

**Headers:**
```
Authorization: Bearer <user-jwt>
X-User-Id: <userId-UUID>
```

**Query Params:**
| Param | Type | Description |
|---|---|---|
| `deliveryAddress` | UUID | Address UUID selected by the user on address screen |

**Example:**
```
GET /api/v1/booking/checkout?deliveryAddress=abc123-...
Headers: X-User-Id: d38a9231-...
```

**Success Response — 201:**
```json
{
  "success": true,
  "message": "2 booking(s) created — one per shop. Pay for each package when it arrives.",
  "statusCode": 201,
  "data": {
    "totalBookings": 2,
    "couponApplied": "BIG1000",
    "expiresAt": "2026-04-15T10:40:00Z",
    "bookings": [
      {
        "bookingId": "f3a1c2d4-1234-5678-abcd-000000000001",
        "shopId": "66a06de3-28ff-4caf-8945-0eed6e9b45d8",
        "itemCount": 1,
        "totalAmountPaise": "118784",
        "totalAmountRupees": 1187.84,
        "breakdownRupees": {
          "subTotal": 1998.00,
          "itemDiscount": 0.00,
          "couponDiscount": -1000.00,
          "gst": 179.64,
          "delivery": 0.00,
          "serviceCharge": 30.00,
          "grandTotal": 1207.64
        },
        "expiresAt": "2026-04-15T10:40:00Z",
        "status": "Initiated"
      }
    ]
  }
}
```

> **Frontend action:**  
> - Store each `bookingId` mapped to its `shopId` and `totalAmountRupees`  
> - Navigate to **Payment Page** passing bookings array  
> - Show a countdown timer — bookings expire in **5 minutes**  
> - If timer hits 0, call this endpoint again to recreate bookings  

**Error Responses:**
| HTTP | Scenario |
|---|---|
| 400 | Cart is empty or has validation issues |
| 409 | An item is out of stock or quantity unavailable |
| 429 | Checkout already in progress (duplicate tap) |
| 500 | Cart service unreachable |

---

## Step B — Place Order (Choose Payment Method)

Call once per booking. For a 2-shop cart, call this twice (once per bookingId).

```
POST /api/v1/payment
```

**Required by:** `ROLE_USER`

### Option 1 — Cash on Delivery (COD)

```json
{
  "gateway":            "cod",
  "bookingId":          "f3a1c2d4-1234-5678-abcd-000000000001",
  "userId":             "d38a9231-b647-4de8-9b2f-9d01a1c472c0",
  "idempotencyKey":     "user-d38a-booking-f3a1-1713200000000",
  "pgPaymentAmount":    "0",
  "pgPayment":          false,
  "pointPayment":       false,
  "pointPaymentAmount": null
}
```

**Success Response — 200:**
```json
{
  "success": true,
  "message": "Order confirmed! Pay cash to the delivery partner on arrival.",
  "statusCode": 201,
  "data": {
    "bookingId":          "f3a1c2d4-1234-5678-abcd-000000000001",
    "paymentId":          "e7f8a9b0-5678-1234-efgh-111111111111",
    "transactionId":      "c1d2e3f4-9999-8888-7777-222222222222",
    "paymentMode":        "CASH_ON_DELIVERY",
    "totalAmountPaise":   "118784",
    "status":             "PENDING",
    "instructions":       "Keep exact change ready. Generate an OTP before your delivery partner arrives."
  }
}
```

> **Store `transactionId`** — required for OTP generation and QR payment.

---

### Option 2 — Online (PhonePe)

```json
{
  "gateway":            "phonepe",
  "bookingId":          "f3a1c2d4-1234-5678-abcd-000000000001",
  "userId":             "d38a9231-b647-4de8-9b2f-9d01a1c472c0",
  "idempotencyKey":     "user-d38a-booking-f3a1-1713200000001",
  "pgPaymentAmount":    "1187.84",
  "pgPayment":          true,
  "pointPayment":       false,
  "pointPaymentAmount": null
}
```

**Success Response — 200:**
```json
{
  "success": true,
  "message": "Payment Created Successfully",
  "statusCode": 201,
  "data": {
    "bookingId":     "f3a1c2d4-...",
    "paymentId":     "e7f8a9b0-...",
    "transactions":  "transactions"
  }
}
```

> Use the `token` from PhonePe SDK to open the PhonePe payment sheet on mobile.

---

**Common Error Responses (both payment options):**
| HTTP | Scenario |
|---|---|
| 400 | Validation error — invalid gateway, missing fields |
| 403 | Booking does not belong to this user |
| 404 | Booking not found |
| 409 | Booking already paid or not in Initiated state |

---

---

# PART 2 — COD Payment Collection (At Doorstep)

---

## COD Option A — Cash + OTP

### Step C1 — User Generates OTP

The user taps **"Generate OTP"** in the app when the delivery partner rings the bell.

```
POST /api/v1/payment/cod/generate-otp
```

**Required by:** `ROLE_USER`

**Request Body:**
```json
{
  "transactionId": "c1d2e3f4-9999-8888-7777-222222222222"
}
```

**Success Response — 200:**
```json
{
  "success": true,
  "message": "OTP generated successfully",
  "statusCode": 200,
  "data": {
    "otp":              "483921",
    "expiresInMinutes": 10,
    "transactionId":    "c1d2e3f4-9999-8888-7777-222222222222",
    "message":          "Show this OTP to your delivery partner to confirm cash payment."
  }
}
```

> - Show OTP prominently on screen  
> - Show 10-minute countdown timer  
> - Add "Regenerate OTP" button (max 3 regenerations per order)  

**Error Responses:**
| HTTP | Scenario | Message to show |
|---|---|---|
| 403 | Transaction not owned by user | Generic error |
| 409 | Already confirmed | "Payment is already complete" |
| 409 | Transaction not PENDING | Generic error |
| 429 | Too many OTP requests | "OTP limit reached. Contact support." |

---

### Step C2 — Delivery Partner Confirms Cash Received

**Required by:** `ROLE_DELIVERY` (delivery partner app only)

```
POST /api/v1/payment/cod/confirm
```

**Request Body:**
```json
{
  "transactionId":        "c1d2e3f4-9999-8888-7777-222222222222",
  "otp":                  "483921",
  "collectedAmountPaise": "118784"
}
```

| Field | Notes |
|---|---|
| `otp` | Exactly 6 numeric digits from customer's screen |
| `collectedAmountPaise` | Must **exactly match** the order total |

**Success Response — 200:**
```json
{
  "success": true,
  "message": "Cash payment confirmed successfully.",
  "statusCode": 200,
  "data": {
    "transactionId": "c1d2e3f4-9999-8888-7777-222222222222",
    "status":        "SUCCESS",
    "paymentMode":   "CASH_ON_DELIVERY"
  }
}
```

**Error Responses:**
| HTTP | Scenario | App message |
|---|---|---|
| 409 | OTP expired | "OTP expired. Ask customer to regenerate." |
| 409 | Wrong OTP | "Invalid OTP. Check and retry." |
| 409 | Amount mismatch | "Collected amount does not match order total." |
| 409 | Already confirmed (idempotent) | Treat as success |
| 429 | Concurrent confirmation in progress | Retry after 2 seconds |

---

## COD Option B — Digital Payment via QR

Use this when the customer prefers to pay online (UPI/PhonePe) instead of cash.  
Money goes directly to the company's merchant account.

### Step D1 — Rider Generates QR Code

**Required by:** `ROLE_DELIVERY`

```
POST /api/v1/payment/cod/generate-payment-qr
```

**Request Body:**
```json
{
  "transactionId": "c1d2e3f4-9999-8888-7777-222222222222"
}
```

**Success Response — 200:**
```json
{
  "success": true,
  "message": "Payment QR generated.",
  "statusCode": 200,
  "data": {
    "qrImageBase64":   "data:image/png;base64,iVBORw0KGgo...",
    "paymentUrl":      "https://mercury.phonepe.com/transact/v3?token=xxx",
    "amountRupees":    1187.84,
    "amountPaise":     "118784",
    "merchantOrderId": "c1d2e3f4-9999-8888-7777-...",
    "transactionId":   "c1d2e3f4-9999-8888-7777-222222222222",
    "expiresInMinutes": 10,
    "instructions":    "Customer scans QR → pays via any UPI app → payment auto-confirmed."
  }
}
```

> - Render `qrImageBase64` directly as `<img src="...">` or in a native Image component  
> - Show `amountRupees` so the customer knows what they're paying  
> - After showing the QR, start polling Step D2  

---

### Step D2 — Rider Polls for Payment Confirmation

Poll every **3 seconds** after QR is shown. Stop when `"paid": true` or after 10 minutes.

**Required by:** `ROLE_DELIVERY`

```
GET /api/v1/payment/cod/qr-status/{transactionId}
```

**Example:**
```
GET /api/v1/payment/cod/qr-status/c1d2e3f4-9999-8888-7777-222222222222
```

**Success Response — 200:**
```json
{
  "success": true,
  "message": "Payment status fetched.",
  "statusCode": 200,
  "data": {
    "transactionId":  "c1d2e3f4-9999-8888-7777-222222222222",
    "status":         "SUCCESS",
    "paymentStatus":  "SUCCESS",
    "amountPaise":    "118784",
    "paid":           true
  }
}
```

> When `"paid": true` → show "Payment Received ✓" to rider  
> When `"status": "PENDING"` → keep polling  
> If still PENDING after 10 minutes → fall back to cash + OTP flow  

**Polling pseudocode:**
```js
async function pollQrStatus(transactionId) {
  const MAX_ATTEMPTS = 200   // 200 × 3s = 10 minutes
  let attempts = 0
  
  while (attempts < MAX_ATTEMPTS) {
    const res = await GET(`/api/v1/payment/cod/qr-status/${transactionId}`)
    if (res.data.paid) {
      showSuccess("Payment Received!")
      return
    }
    await sleep(3000)
    attempts++
  }
  showMessage("QR payment timed out. Collect cash and use OTP confirmation.")
}
```

---

## Money Flow Summary

```
CASH:
  Customer ──cash──► Rider ──OTP confirm──► Backend marks SUCCESS

QR / UPI:
  Customer ──UPI scan──► PhonePe ──► Company merchant account
                                         │
                                    PhonePe webhook fires
                                         │
                               Backend marks transaction SUCCESS
                                         │
                              Rider's poll returns "paid: true"
```

---

---

# PART 3 — Validate & Refund

---

## Validate Payment Status

```
GET /api/v1/payment/validate-payment?merchantOrderId=<UUID>&gateway=phonepe
```

**Required by:** `ROLE_USER` or internal

---

## Refund

```
POST /api/v1/payment/refund?gateway=cod&transactionId=<UUID>&amount=<paise>&userId=<UUID>
```

**COD Refund Response:**
```json
{
  "success": true,
  "message": "COD refund initiated. Amount will be credited to your wallet.",
  "statusCode": 202,
  "data": {
    "transactionId": "...",
    "refundAmount":  "118784",
    "refundMode":    "WALLET_CREDIT",
    "status":        "REFUND_INITIATED"
  }
}
```

---

---

# PART 4 — Complete Flow Diagrams

---

## Full COD Flow (Cash)

```
CART PAGE
    │  tap "Proceed to Checkout"
    ▼
GET /api/v1/booking/checkout?deliveryAddress=<UUID>
Header: X-User-Id: <userId>
    │  ← 201 { bookings: [{ bookingId, totalAmountRupees, expiresAt }] }
    │  Store bookingId(s) in state
    ▼
ADDRESS / ORDER REVIEW PAGE
    │  tap "Place Order"
    ▼
POST /api/v1/payment  { gateway:"cod", bookingId, ... }
    │  ← 200 { transactionId, status:"PENDING", paymentMode:"CASH_ON_DELIVERY" }
    │  Store transactionId in state
    ▼
ORDER CONFIRMED SCREEN
"Pay ₹1,187.84 cash on arrival"
    │
    │  [Delivery partner rings bell]
    │
    ▼
tap "Generate OTP"
POST /api/v1/payment/cod/generate-otp  { transactionId }
    │  ← 200 { otp:"483921", expiresInMinutes:10 }
    ▼
OTP SCREEN — show 4 8 3 9 2 1 to rider
    │
    │  Rider enters OTP in their app
    │  POST /api/v1/payment/cod/confirm  ← RIDER APP
    │
    ▼
PUSH NOTIFICATION → "Payment of ₹1,187.84 confirmed. Thank you!"
```

---

## Full COD Flow (QR / Digital)

```
[After order is placed — same steps as above until delivery arrives]

Rider taps "Customer wants to pay digitally"
POST /api/v1/payment/cod/generate-payment-qr  { transactionId }
    │  ← 200 { qrImageBase64, amountRupees:1187.84 }
    ▼
RIDER SCREEN — shows QR code
    │
    │  Customer opens UPI app, scans QR, pays ₹1,187.84
    │  Money → PhonePe → Company merchant account
    │  PhonePe fires webhook:
    │  POST /api/v1/payment/webhook/phonepe
    │      (handled internally — no frontend action needed)
    │
    │  Rider polls every 3s:
    │  GET /api/v1/payment/cod/qr-status/{transactionId}
    │
    ▼
"paid": true  →  RIDER SCREEN: "Payment Received ✓"
```

---

## Multi-Shop Cart Flow

```
Cart has items from Shop A and Shop B

GET /api/v1/booking/checkout
    │
    ← bookings: [
        { bookingId: "AAA", shopId: "Shop-A", totalAmountRupees: 499.00 },
        { bookingId: "BBB", shopId: "Shop-B", totalAmountRupees: 299.00 }
      ]

For each booking, call payment API:
POST /api/v1/payment  { gateway:"cod", bookingId:"AAA" }  → transactionId-A
POST /api/v1/payment  { gateway:"cod", bookingId:"BBB" }  → transactionId-B

Day 1 — Shop A delivers:
  User generates OTP for transactionId-A → rider confirms
  → Booking A: payment SUCCESS

Day 3 — Shop B delivers:
  User generates OTP for transactionId-B → rider confirms
  → Booking B: payment SUCCESS
```

---

---

# PART 5 — Idempotency Key Guide

Generate a unique key per payment attempt:
```
{userId}-{bookingId}-{epochMillis}
```

Example: `d38a9231-f3a1c2d4-1713200000000`

- Must be 8–64 characters
- Use the **same key** if retrying the exact same failed request
- Generate a **new key** for a fresh payment attempt

---

# PART 6 — Error Code Reference

| HTTP | Meaning | Action |
|---|---|---|
| 400 | Validation error | Show field-level error message |
| 401 | Missing/expired JWT | Redirect to login |
| 403 | Resource does not belong to user | Show generic error |
| 404 | Resource not found | Show generic error |
| 409 | Business rule conflict (out of stock, wrong OTP, already paid) | Show specific message from `message` field |
| 429 | Too many requests / lock contention | Retry after delay |
| 500 | Server error | Show "Something went wrong, try again" |

Error response shape is always:
```json
{
  "success": false,
  "message": "Human-readable reason",
  "data": null,
  "statusCode": 409
}
```

---

# PART 7 — Environment Variables (for your reference)

| Variable | Description |
|---|---|
| `INTERNAL_API_KEY` | Must match `internal.api.key` in this service (`internal-api` default) |
| `PHONEPE_CLIENT_ID` | PhonePe merchant client ID |
| `PHONEPE.CLIENT_SECRET` | PhonePe secret |
| `PHONEPE.CLIENT_VERSION` | PhonePe API version |
