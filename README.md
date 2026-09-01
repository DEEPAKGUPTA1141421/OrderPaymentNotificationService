# OrderPaymentNotificationService

A production-grade Spring Boot microservice that handles **payments, order lifecycle, wallet & loyalty, multi-channel notifications, and real-time chat** for a modern e-commerce platform.

---

## What We Built

This service is the financial and communication backbone of the platform. When a customer places an order, every subsequent interaction — charging their card, sending a confirmation push, earning loyalty points, generating an invoice, connecting them with the seller on chat — flows through this service.

It is designed as an **event-driven microservice** that talks to the rest of the ecosystem (ProductService, DeliveryService) over Kafka and Feign HTTP, while exposing a secure REST API for the mobile/web clients.

---

## Core Domains

### 1. Payment Processing
Multiple payment methods unified behind a **Strategy pattern** (`PaymentGateway` interface):

| Method | How it works |
|---|---|
| **PhonePe (online)** | Initiates a checkout session via PhonePe SDK; webhook confirms settlement |
| **Wallet** | Deducts from pre-funded in-app wallet balance atomically |
| **Loyalty Points** | Redeems earned points as partial/full payment |
| **Cash on Delivery** | 6-digit OTP generated at placement, verified by delivery partner at doorstep |
| **Doorstep QR** | PhonePe QR code generated at delivery time for in-person UPI payment |

Distributed Redis locks prevent double-processing of webhooks and concurrent payment races.

### 2. Order Lifecycle
Bookings follow a strict state machine:

```
INITIATED → CONFIRMED → PROCESSING → OUT_FOR_DELIVERY → DELIVERED
```

- **Stock hold:** 10-minute inventory lock during checkout
- **Booking expiry:** 5-minute pricing hold; auto-cancelled if payment not completed
- **Flyway migrations** manage schema evolution across environments

### 3. Wallet & Loyalty Program
- **Wallet:** Balance stored in paise (₹1 = 100 paise); supports freeze/unfreeze for dispute resolution
- **Loyalty tiers:** BRONZE → GOLD (5 000 pts) → PLATINUM (20 000 pts)
- **Earn rate:** ₹100 spent = 10 points
- **Redemption:** 1 point = ₹0.25; minimum 100 pts; daily cap 20 000 pts; points expire in 1 year
- Idempotency via Redis prevents duplicate top-ups and double-redemptions

### 4. Multi-Channel Notification System
A single `NotificationDispatcher` fans out to four channels asynchronously:

| Channel | Provider |
|---|---|
| **Push (mobile)** | Firebase Cloud Messaging (FCM) |
| **In-App** | Stored in PostgreSQL; served via paginated REST endpoint |
| **Email** | SendGrid |
| **SMS** | Twilio |

Kafka events from other services (order placed, payment confirmed, delivery status) trigger notifications. Redis deduplication (24-hour window) guarantees each event fires exactly once.

Users control opt-in/opt-out per category (ORDER_UPDATES, PAYMENT, LOYALTY, PROMOTIONS) per channel.

### 5. Real-Time Chat (SendBird)
- A **customer-seller chat channel** is automatically created per order
- **Support ticket channels** connect customers to the support agent
- Session tokens (JWT-style) are issued for WebSocket connections
- Delivered order channels are archived after 7 days
- SendBird webhooks are ingested and relayed as internal Kafka events

### 6. Shop Ranking
A scheduled service consumes `order-placed` Kafka events and updates seller shop scores (order frequency, delivery time, review signals) every 5 minutes.

### 7. Document Generation
PDF invoices and receipts are generated on-demand using OpenPDF, with QR codes (ZXing) embedded for quick order lookup.

---

## System Architecture

```
Mobile / Web Client
        │  JWT (RS256)
        ▼
┌─────────────────────────────────────────────────────────────┐
│              OrderPaymentNotificationService                 │
│                       :8082                                  │
│                                                              │
│  Controllers ──► Services ──► Repositories ──► PostgreSQL    │
│       │              │                                       │
│       │         Redis (locks, cache, OTP, dedup)             │
│       │                                                      │
│       └──► Kafka Producer ──────────────────────────────┐   │
│                                                         │   │
│  Kafka Consumers ◄──────────────────────────────────────┘   │
│  (notification, receipt, chat lifecycle, ranking)           │
└───────────────────────────────┬─────────────────────────────┘
                                │
          ┌─────────────────────┼──────────────────────┐
          │                     │                      │
          ▼                     ▼                      ▼
    ProductService       DeliveryService         External APIs
    (OpenFeign)          (OpenFeign)          PhonePe · FCM · SendBird
                                              Twilio · SendGrid
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language & Runtime** | Java 17 |
| **Framework** | Spring Boot 3.5.4 |
| **API** | Spring MVC (REST), Spring Security |
| **Database** | PostgreSQL + Spring Data JPA + Hibernate |
| **Schema Migrations** | Flyway |
| **Message Broker** | Apache Kafka |
| **Cache & Distributed Locks** | Redis (Lettuce client) |
| **Payment Gateway** | PhonePe SDK 2.1.0 |
| **Push Notifications** | Firebase Admin SDK 9.3.0 |
| **SMS** | Twilio SDK 9.13.0 |
| **Email** | SendGrid SDK 4.9.3 |
| **Real-Time Chat** | SendBird REST API |
| **Inter-Service HTTP** | Spring Cloud OpenFeign |
| **PDF Generation** | OpenPDF 1.3.30 |
| **QR Codes** | ZXing 3.5.2 |
| **Auth** | JWT (RS256 asymmetric keys) |
| **Build Tool** | Maven |
| **Config Management** | dotenv-kotlin (env var injection) |

---

## Key Design Decisions

### Event-Driven Architecture
Heavy operations (notifications, receipts, ranking updates, chat lifecycle) are decoupled from the HTTP request path via Kafka. The REST endpoint returns immediately; background consumers handle the side effects.

### Strategy Pattern for Payments
`PaymentGateway` is an interface. `PaymentGatewayFactory` selects the right implementation at runtime (`phonepe`, `cod`, `wallet`, `qr`). Adding a new payment method requires implementing one interface — nothing else changes.

### Idempotency Everywhere
- Webhook re-deliveries: Redis lock keyed on `transactionId`
- Notification dedup: Redis `SETNX` keyed on `eventId`
- Wallet top-ups: Redis idempotency key per request
- COD OTP: max 3 regenerations, constant-time comparison

### Horizontal Security
User identity (`userId`) is always extracted from the **validated JWT** inside the `JwtAuthenticationFilter`, never trusted from the request body. This prevents horizontal privilege escalation.

### Async Notification Dispatch
The `NotificationDispatcher` uses Spring `@Async` with a dedicated thread pool (5 core / 20 max / 500 queue) so notification failures never impact payment response latency.

---

## REST API Summary

All endpoints are prefixed `/api/v1` and require a valid JWT (`Authorization: Bearer <token>`).

| Group | Key Endpoints |
|---|---|
| **Payment** | `POST /payment` · `POST /payment/cod/generate-otp` · `POST /payment/cod/confirm` · `POST /payment/webhook/{gateway}` · `POST /payment/refund` |
| **Wallet** | `GET /users/wallet` · `GET /users/wallet/transactions` · `POST /users/wallet/add-money` |
| **Loyalty** | `GET /loyalty` · `GET /loyalty/transactions` · `POST /loyalty/redeem` |
| **Orders** | `POST /bookings` · `GET /bookings/{id}` · `GET /bookings/track/{id}` |
| **Notifications** | `GET /notifications` · `PUT /notifications/{id}/read` · `GET /notifications/preferences` · `PUT /notifications/preferences` |
| **Devices** | `POST /devices` (register FCM token) · `DELETE /devices/{token}` |
| **Chat** | `POST /chat/token` · `POST /chat/channel/order/{orderId}` · `POST /chat/support` |
| **Receipts** | `GET /receipts/{bookingId}` · `POST /receipts` |
| **Health** | `GET /health` |

---

## Environment Variables

The service is fully configured via environment variables (no secrets in source):

```
DB_URL                   PostgreSQL JDBC URL
REDIS_HOST / PORT / AUTH Redis connection
KAFKA_BOOTSTRAP_SERVERS  Kafka brokers
PHONEPE_CLIENT_ID/SECRET PhonePe credentials
FCM_*                    Firebase service account
TWILIO_ACCOUNT_SID/AUTH  Twilio credentials
SENDGRID_API_KEY         SendGrid key
SENDBIRD_APP_ID/TOKEN    SendBird credentials
```

RS256 JWT verification uses the bundled key pair under `src/main/resources/keys/`
(identical to ProductClientService's — the service that actually issues
tokens). Not env-configurable on purpose: this service only verifies tokens,
never issues its own, so its public key must always match the issuer's.

---

## Database Schema Overview

| Table | Purpose |
|---|---|
| `bookings` | Order header with state, timestamps, user/seller refs |
| `booking_items` | Line items (product id, qty, price, image URL) |
| `payments` | Payment record linked to booking |
| `transactions` | Individual payment attempts (method, status, gateway ref) |
| `wallets` | User wallet accounts (balance in paise) |
| `wallet_transactions` | Wallet credit/debit history |
| `loyalty_accounts` | Points balance + tier per user |
| `loyalty_transactions` | Points earn/redeem history |
| `device_tokens` | FCM tokens per user/device |
| `in_app_notifications` | Stored notifications (read/unread) |
| `notification_preferences` | User channel opt-in settings |
| `saved_payment_methods` | Tokenised cards and UPI handles |
| `receipts` | Invoice records (PDF blob or S3 ref) |
| `chat_channel_mappings` | SendBird channelUrl ↔ orderId/ticketId |

Schema is managed by **Flyway** (migrations in `src/main/resources/db/migration/`).
