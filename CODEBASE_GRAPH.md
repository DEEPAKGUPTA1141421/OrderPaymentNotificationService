# OrderPaymentNotificationService Graph

Generated from `.` on 2026-05-03.

## High-Level Architecture

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
        DTOs[DTOs]
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

## Package Map

```mermaid
flowchart TD
    Root[com.OrderPaymentNotificationService.OrderPaymentNotificationService]
    Root --> Controller
    Root --> Service
    Root --> Repository
    Root --> Model
    Root --> DTO
    Root --> Configuration
    Root --> Utils
    Root --> filter
    Root --> constant

    Service --> ServiceChat[Service.chat]
    Service --> ServiceRanking[Service.ranking]
    DTO --> DTOChat[DTO.chat]
    DTO --> DTONotification[DTO.NotificationDto]
    DTO --> DTOOrder[DTO.OrderDto]
    DTO --> DTOReceipt[DTO.receipt]
    DTO --> DTODelivery[DTO.delivery]
    DTO --> DTONetwork[DTO.network]
    DTO --> DTORanking[DTO.ranking]
    DTO --> DTOWallet[DTO.WalletLoyaltyDto]
    DTO --> DTOBuyNow[DTO.BuyNow]
    Utils --> Strategy[Utils.Strategy]
    Utils --> Network[Utils.network]
```

## Main Feature Flows

```mermaid
flowchart TD
    subgraph Booking
        BookingController --> BookingService
        BookingController --> BookingQueryService
        DirectPurchaseController --> DirectPurchaseService
        SellerBookingController --> SellerBookingService
        OrderTrackingController --> OrderTrackingService
        BookingService --> ProductClient
        DirectPurchaseService --> ProductClient
        BookingService --> BookingRepository
        BookingQueryService --> BookingRepository
        BookingQueryService --> PaymentRepository
        SellerBookingService --> BookingRepository
        SellerBookingService --> BookingItemRepository
        SellerBookingService --> PaymentRepository
        OrderTrackingService --> BookingRepository
        OrderTrackingService --> PaymentRepository
    end

    subgraph Payment
        PaymentController --> PaymentGatewayFactory
        PaymentController --> CodPaymentService
        PaymentController --> QrPaymentService
        PaymentGatewayFactory --> PhonePePaymentGateway
        PaymentGatewayFactory --> CodPaymentGateway
        PhonePePaymentGateway --> PhonePeService
        PhonePePaymentGateway --> ReceiptProducerService
        CodPaymentGateway --> ReceiptProducerService
        CodPaymentService --> TransactionRepository
        CodPaymentService --> PaymentRepository
        QrPaymentService --> TransactionRepository
        QrPaymentService --> PaymentRepository
    end

    subgraph WalletAndLoyalty
        WalletController --> WalletService
        LoyaltyController --> LoyaltyService
        PaymentMethodController --> SavedPaymentMethodService
        WalletService --> WalletRepository
        WalletService --> WalletTransactionRepository
        WalletService --> PaymentGatewayFactory
        LoyaltyService --> LoyaltyAccountRepository
        LoyaltyService --> LoyaltyTransactionRepository
        LoyaltyService --> WalletService
        SavedPaymentMethodService --> SavedPaymentMethodRepository
    end

    subgraph Notifications
        DeviceTokenController --> DeviceTokenService
        NotificationPreferenceController --> NotificationPreferenceService
        InAppNotificationController --> InAppNotificationService
        NotificationEventListener --> NotificationDispatcher
        NotificationDispatcher --> InAppNotificationService
        NotificationDispatcher --> FcmPushNotificationService
        NotificationDispatcher --> NotificationFactory
        NotificationFactory --> EmailNotificationService
        NotificationFactory --> SmsNotificationService
        FcmPushNotificationService --> DeviceTokenRepository
        InAppNotificationService --> InAppNotificationRepository
        NotificationPreferenceService --> NotificationPreferenceRepository
    end

    subgraph ReceiptAndRanking
        ReceiptController --> ReceiptService
        ReceiptProducerService --> Kafka
        ReceiptConsumerService --> ReceiptGeneratorService
        ReceiptConsumerService --> ReceiptRepository
        OrderEventListener --> ShopStatsRepository
        RankingService --> ShopStatsRepository
        RankingService --> ScoreCalculator
        RankingController --> Redis
    end

    subgraph Sponsorships
        SponsoredSlotController --> SponsoredSlotService
        SponsoredSectionController --> SponsoredSectionService
        SponsoredSlotService --> SponsoredSlotRepository
        SponsoredSectionService --> SponsoredSectionRepository
    end
```

## Chat / SendBird Flow

```mermaid
flowchart TD
    AuthUser[Authenticated UserPrincipal]
    ChatController["ChatController /api/chat"]
    TokenEndpoint["POST /api/chat/token"]
    SupportEndpoint["POST /api/chat/support/ticket"]
    ChatTokenService
    ChatChannelService
    SendBirdClient
    ChatChannelMappingRepository
    SendBirdAPI[SendBird Platform]
    WebhookController["SendBirdWebhookController /webhooks/sendbird/message"]
    ChatMessageProducer
    KafkaProducerService
    KafkaTopic["chat.message.received"]
    ChatLifecycleConsumer
    BookingRepository

    AuthUser --> ChatController
    ChatController --> TokenEndpoint --> ChatTokenService
    ChatController --> SupportEndpoint --> ChatChannelService
    ChatTokenService --> SendBirdClient
    ChatChannelService --> SendBirdClient
    ChatChannelService --> ChatChannelMappingRepository
    SendBirdClient --> SendBirdAPI
    SendBirdAPI --> WebhookController
    WebhookController --> ChatMessageProducer --> KafkaProducerService --> KafkaTopic
    KafkaTopic --> NotificationEventListener
    ChatLifecycleConsumer --> BookingRepository
    ChatLifecycleConsumer --> ChatChannelService
```

## Persistence Layer

```mermaid
flowchart LR
    BookingRepository --> Booking
    BookingItemRepository --> BookingItem
    PaymentRepository --> Payment
    TransactionRepository --> Transaction
    ReceiptRepository --> Receipt
    WalletRepository --> Wallet
    WalletTransactionRepository --> WalletTransaction
    LoyaltyAccountRepository --> LoyaltyAccount
    LoyaltyTransactionRepository --> LoyaltyTransaction
    SavedPaymentMethodRepository --> SavedPaymentMethod
    DeviceTokenRepository --> DeviceToken
    InAppNotificationRepository --> InAppNotification
    NotificationPreferenceRepository --> NotificationPreference
    ChatChannelMappingRepository --> ChatChannelMapping
    ShopStatsRepository --> ShopStats
    SponsoredSlotRepository --> SponsoredSlot
    SponsoredSectionRepository --> SponsoredSection
```

## External Interfaces

```mermaid
flowchart LR
    ProductClient --> ProductService["Product service\n/internal/v1/cart/{userId}\n/internal/v1/product/{productId}/variant/{variantId}"]
    PhonePeService --> PhonePeGateway[PhonePe SDK / Gateway]
    SendBirdClient --> SendBirdRest["SendBird REST API"]
    FcmPushNotificationService --> FirebaseFCM[Firebase Cloud Messaging]
    EmailNotificationService --> SendGridAPI[SendGrid API]
    SmsNotificationService --> TwilioAPI[Twilio API]
    KafkaProducerService --> KafkaNotification[notification topic]
    ReceiptProducerService --> KafkaReceipt[receipt events]
    BookingConfirmedProducer --> KafkaDelivery[booking confirmed events]
```
