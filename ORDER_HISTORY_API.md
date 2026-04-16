# Order History API — Frontend Integration Guide

Base URL: `http://<host>:8082`  
Auth: All endpoints require `Authorization: Bearer <jwt>` (ROLE_USER).

---

## Table of Contents

1. [Order List Page](#1-order-list-page)
2. [Order Detail Page](#2-order-detail-page)
3. [Status Reference](#3-status-reference)
4. [Payment Mode Reference](#4-payment-mode-reference)
5. [Pagination Guide](#5-pagination-guide)
6. [Error Responses](#6-error-responses)
7. [UI Rendering Guide](#7-ui-rendering-guide)

---

## 1. Order List Page

Returns a paginated list of the user's bookings, most recent first.

```
GET /api/v1/booking
```

### Query Parameters

| Parameter | Type | Default | Max  | Description               |
|-----------|------|---------|------|---------------------------|
| `page`    | int  | `0`     | —    | Zero-based page number    |
| `size`    | int  | `10`    | `50` | Number of orders per page |

### Example Request

```http
GET /api/v1/booking?page=0&size=10
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
```

### Success Response — 200

```json
{
  "success": true,
  "message": "Orders fetched successfully",
  "statusCode": 200,
  "data": {
    "orders": [
      {
        "bookingId": "d2f3a1b0-1234-5678-abcd-ef0123456789",
        "shopId": "a1b2c3d4-0001-0002-0003-000400050006",
        "status": "CONFIRMED",
        "statusLabel": "Confirmed",
        "itemCount": 3,
        "totalAmountPaise": "149900",
        "totalAmountRupees": "1499.00",
        "paymentStatus": "SUCCESS",
        "paymentMode": "ONLINE",
        "expiresAt": "2025-01-15T10:35:00Z",
        "createdAt": "2025-01-15T10:30:00Z"
      },
      {
        "bookingId": "e3a4b2c1-abcd-1234-5678-fedcba987654",
        "shopId": "b2c3d4e5-1111-2222-3333-444455556666",
        "status": "Initiated",
        "statusLabel": "Order Placed",
        "itemCount": 1,
        "totalAmountPaise": "49900",
        "totalAmountRupees": "499.00",
        "paymentStatus": null,
        "paymentMode": "UNPAID",
        "expiresAt": "2025-01-15T11:05:00Z",
        "createdAt": "2025-01-15T11:00:00Z"
      }
    ],
    "currentPage": 0,
    "pageSize": 10,
    "totalOrders": 24,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### Field Reference

| Field              | Type    | Nullable | Description                                              |
|--------------------|---------|----------|----------------------------------------------------------|
| `bookingId`        | UUID    | No       | Use this to navigate to the detail page                 |
| `shopId`           | UUID    | No       | Seller/shop identifier                                  |
| `status`           | String  | No       | Raw status enum — see [Status Reference](#3-status-reference) |
| `statusLabel`      | String  | No       | Display-ready label for the UI                          |
| `itemCount`        | int     | No       | Number of distinct products in the booking              |
| `totalAmountPaise` | String  | No       | Grand total in paise (÷100 = ₹)                         |
| `totalAmountRupees`| String  | No       | Pre-converted rupee string (2 decimal places)           |
| `paymentStatus`    | String  | **Yes**  | null when payment not yet initiated                     |
| `paymentMode`      | String  | No       | See [Payment Mode Reference](#4-payment-mode-reference) |
| `expiresAt`        | ISO-8601| No       | Booking hold expiry — show countdown if `Initiated`     |
| `createdAt`        | ISO-8601| **Yes**  | null for legacy bookings; use for "Ordered on" display  |

---

## 2. Order Detail Page

Returns full details for a single booking belonging to the authenticated user.

```
GET /api/v1/booking/{bookingId}
```

### Path Parameter

| Parameter   | Type | Description          |
|-------------|------|----------------------|
| `bookingId` | UUID | ID from the list API |

### Example Request

```http
GET /api/v1/booking/d2f3a1b0-1234-5678-abcd-ef0123456789
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
```

### Success Response — 200

```json
{
  "success": true,
  "message": "Order details fetched successfully",
  "statusCode": 200,
  "data": {
    "bookingId": "d2f3a1b0-1234-5678-abcd-ef0123456789",
    "shopId": "a1b2c3d4-0001-0002-0003-000400050006",
    "deliveryAddress": "f4e5d6c7-9999-8888-7777-666655554444",
    "status": "CONFIRMED",
    "statusLabel": "Confirmed",
    "totalAmountPaise": "149900",
    "totalAmountRupees": "1499.00",
    "expiresAt": "2025-01-15T10:35:00Z",
    "createdAt": "2025-01-15T10:30:00Z",
    "items": [
      {
        "bookingItemId": "11112222-3333-4444-5555-666677778888",
        "productId": "aaaa0001-0000-0000-0000-000000000001",
        "variantId": "bbbb0001-0000-0000-0000-000000000001",
        "quantity": 2,
        "unitPricePaise": "49900",
        "unitPriceRupees": "499.00",
        "lineTotalPaise": "99800",
        "lineTotalRupees": "998.00"
      },
      {
        "bookingItemId": "22223333-4444-5555-6666-777788889999",
        "productId": "aaaa0002-0000-0000-0000-000000000002",
        "variantId": "bbbb0002-0000-0000-0000-000000000002",
        "quantity": 1,
        "unitPricePaise": "50100",
        "unitPriceRupees": "501.00",
        "lineTotalPaise": "50100",
        "lineTotalRupees": "501.00"
      }
    ],
    "payment": {
      "paymentId": "cccc1111-0000-0000-0000-000000000001",
      "status": "SUCCESS",
      "totalAmountPaise": "149900",
      "totalAmountRupees": "1499.00",
      "paidAmountPaise": "149900",
      "transactions": [
        {
          "transactionId": "dddd2222-0000-0000-0000-000000000002",
          "method": "GATEWAY",
          "status": "SUCCESS",
          "amountPaise": "149900",
          "amountRupees": "1499.00",
          "orderId": "OD1234567890",
          "createdAt": "2025-01-15T10:30:05+05:30",
          "updatedAt": "2025-01-15T10:31:22+05:30"
        }
      ]
    }
  }
}
```

### COD order example (detail)

```json
{
  "success": true,
  "message": "Order details fetched successfully",
  "statusCode": 200,
  "data": {
    "bookingId": "e3a4b2c1-abcd-1234-5678-fedcba987654",
    "status": "CONFIRMED",
    "statusLabel": "Confirmed",
    "totalAmountPaise": "49900",
    "totalAmountRupees": "499.00",
    "items": [ "..." ],
    "payment": {
      "paymentId": "eeee3333-0000-0000-0000-000000000003",
      "status": "PENDING",
      "totalAmountPaise": "49900",
      "totalAmountRupees": "499.00",
      "paidAmountPaise": "0",
      "transactions": [
        {
          "transactionId": "ffff4444-0000-0000-0000-000000000004",
          "method": "COD",
          "status": "PENDING",
          "amountPaise": "49900",
          "amountRupees": "499.00",
          "orderId": "COD-E3A4B2C1",
          "createdAt": "2025-01-15T11:00:10+05:30",
          "updatedAt": "2025-01-15T11:00:10+05:30"
        }
      ]
    }
  }
}
```

### Items Field Reference

| Field            | Type   | Description                              |
|------------------|--------|------------------------------------------|
| `bookingItemId`  | UUID   | Line-item identifier                     |
| `productId`      | UUID   | Use to fetch product details from catalogue service |
| `variantId`      | UUID   | Specific size/color/variant selected     |
| `quantity`       | int    | Units ordered                            |
| `unitPricePaise` | String | Price per unit in paise at time of order |
| `unitPriceRupees`| String | Pre-converted for display                |
| `lineTotalPaise` | String | `unitPricePaise × quantity`              |
| `lineTotalRupees`| String | Pre-converted for display                |

### Payment Field Reference

| Field              | Type   | Nullable | Description                                       |
|--------------------|--------|----------|---------------------------------------------------|
| `payment`          | Object | **Yes**  | null when user hasn't initiated payment yet       |
| `paymentId`        | UUID   | No       | —                                                 |
| `status`           | String | No       | INITIATED / PENDING / SUCCESS / FAILED / REVERSED |
| `totalAmountPaise` | String | No       | Expected total payment                            |
| `paidAmountPaise`  | String | No       | Amount actually collected so far                  |
| `transactions`     | Array  | No       | One entry per payment leg                         |

### Transaction Field Reference

| Field           | Type         | Description                                           |
|-----------------|--------------|-------------------------------------------------------|
| `method`        | String       | `COD` / `GATEWAY` (PhonePe) / `POINTS`               |
| `status`        | String       | INITIATED / PENDING / SUCCESS / FAILED                |
| `amountPaise`   | String       | Amount covered by this leg                            |
| `orderId`       | String/null  | PhonePe merchant order ID; `COD-XXXXXXXX` for COD    |
| `createdAt`     | ISO-8601+IST | Transaction creation time                             |
| `updatedAt`     | ISO-8601+IST | Last status change                                    |

---

## 3. Status Reference

| `status` value  | `statusLabel`      | What to show in UI                                  |
|-----------------|--------------------|-----------------------------------------------------|
| `Initiated`     | Order Placed       | Show OTP button + countdown timer to `expiresAt`   |
| `CONFIRMED`     | Confirmed          | Show tracking info / delivery details               |
| `CANCELLED`     | Cancelled          | Show cancellation reason if available               |
| `FAILED`        | Failed             | Show retry or support CTA                           |
| `REVERSE`       | Return Initiated   | Show return tracking                                |
| `REVERSE_FAILED`| Return Failed      | Show support contact                                |

---

## 4. Payment Mode Reference

| `paymentMode` | What it means                                  |
|---------------|------------------------------------------------|
| `COD`         | Cash on delivery — rider will collect cash     |
| `ONLINE`      | Fully paid online via PhonePe / UPI            |
| `POINTS`      | Fully paid using loyalty points                |
| `MIXED`       | Split — part online gateway, part points       |
| `UNPAID`      | No payment initiated yet (booking just placed) |
| `UNKNOWN`     | Payment exists but transaction records missing |

---

## 5. Pagination Guide

The list API uses zero-based pagination.

```
GET /api/v1/booking?page=0&size=10    ← first page
GET /api/v1/booking?page=1&size=10    ← second page
GET /api/v1/booking?page=2&size=10    ← third page
```

### Pagination fields in response

```json
{
  "currentPage": 0,
  "pageSize": 10,
  "totalOrders": 24,
  "totalPages": 3,
  "hasNext": true,
  "hasPrevious": false
}
```

**Infinite scroll** — load next page when user reaches bottom, check `hasNext` before fetching.  
**Numbered pagination** — use `totalPages` to render page buttons.  
**"Load more" button** — same as infinite scroll; hide button when `hasNext` is false.

---

## 6. Error Responses

### 401 Unauthorized — missing or expired JWT

```json
{ "success": false, "message": "Unauthorized", "statusCode": 401, "data": null }
```

### 403 Forbidden — booking belongs to a different user

```json
{ "success": false, "message": "Access denied", "statusCode": 403, "data": null }
```

### 404 Not Found — bookingId doesn't exist

```json
{ "success": false, "message": "Order not found: <uuid>", "statusCode": 404, "data": null }
```

---

## 7. UI Rendering Guide

### Order List Card

```
┌──────────────────────────────────────────────────────┐
│  Shop: {shopId}               Confirmed ✓            │
│  3 items                      ₹{totalAmountRupees}   │
│  Paid via ONLINE                                     │
│  Ordered on {createdAt}          >  View Details     │
└──────────────────────────────────────────────────────┘
```

**Key decisions:**

| Condition                       | UI action                                           |
|---------------------------------|-----------------------------------------------------|
| `status == "Initiated"`         | Show OTP button + countdown (`expiresAt - now`)     |
| `paymentMode == "COD"` + PENDING| Show "Pay on delivery — keep ₹{amount} ready"       |
| `paymentStatus == "FAILED"`     | Show "Payment failed — retry" CTA                   |
| `createdAt == null`             | Show empty string or "—" (legacy order)             |

### Order Detail Screen

**Sections to render:**

1. **Header** — `statusLabel` with colour badge, `createdAt`, `shopId`
2. **Delivery** — `deliveryAddress` UUID (resolve via address service using this ID)
3. **Items** — for each item: `productId` + `variantId` (resolve name/image via catalogue), `quantity`, `unitPriceRupees`, `lineTotalRupees`
4. **Payment Summary** — `payment.totalAmountRupees`, `payment.paidAmountPaise`, `payment.status`
5. **Transaction Breakdown** — for each transaction: method badge, amount, status, `createdAt`

**COD-specific UI:**

```
payment.method == "COD" && payment.status == "PENDING"
→ Show "Generate OTP" button → POST /api/v1/payment/cod/generate-otp
→ Show OTP countdown + instructions to customer
```

**Polling for QR payment:**

```
Every 3 seconds: GET /api/v1/payment/cod/qr-status/{transactionId}
Stop when: response.data.paid == true
```

### Amount Formatting

Always use `totalAmountRupees` (pre-formatted string with 2 decimals).  
Display as: `₹{totalAmountRupees}` — example: `₹1,499.00` (apply locale comma-formatting on the frontend).

---

*Generated: 2026-04-16 | Service: OrderPaymentNotificationService | Port: 8082*
