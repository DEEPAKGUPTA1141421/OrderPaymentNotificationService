# Graphify Output

Generated from `.` on 2026-05-03.

## Files

- `architecture.mmd` - high-level service architecture
- `packages.mmd` - package and module layout
- `feature-flows.mmd` - main controller, service, repository flows
- `chat-sendbird.mmd` - chat token, support channel, webhook, and Kafka flow
- `persistence.mmd` - repository to entity map
- `external-interfaces.mmd` - external systems and integration points

## Preview

```mermaid
flowchart LR
    Client[Mobile / Web Clients]
    SendBird[SendBird Webhooks]
    Kafka[Kafka Topics]
    Redis[(Redis)]
    Postgres[(PostgreSQL)]
    ProductSvc[Product Service]
    PhonePe[PhonePe]
    Firebase[Firebase FCM]
    SendGrid[SendGrid]
    Twilio[Twilio]

    subgraph App[OrderPaymentNotificationService]
        Security[JWT Security Filter]
        Controllers[REST Controllers]
        Services[Application Services]
        Repositories[Spring Data JPA Repositories]
        Models[JPA Entities]
        Config[Configuration]
        Feign[ProductClient Feign]
        Gateways[PaymentGateway Strategy]
        Consumers[Kafka Consumers / Scheduled Jobs]
        Producers[Kafka Producers]
    end

    Client --> Security --> Controllers
    Controllers --> Services
    Controllers --> Gateways
    Services --> Repositories --> Models --> Postgres
    Services --> Redis
    Gateways --> Repositories
    Gateways --> PhonePe
    Gateways --> Producers
    Services --> Feign --> ProductSvc
    Services --> Producers --> Kafka
    Kafka --> Consumers --> Services
    SendBird --> Controllers
    Services --> Firebase
    Services --> SendGrid
    Services --> Twilio
    Config --> Security
    Config --> Kafka
    Config --> Redis
```
