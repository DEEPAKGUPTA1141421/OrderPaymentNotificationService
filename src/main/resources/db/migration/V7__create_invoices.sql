-- Seller-authored (POS-style) invoices — distinct from the auto-generated
-- buyer-order receipts already in the `receipts` table.

CREATE TABLE invoice_customers (
    id          UUID PRIMARY KEY,
    seller_id   UUID NOT NULL,
    name        VARCHAR(255) NOT NULL,
    phone       VARCHAR(32),
    email       VARCHAR(255),
    gstin       VARCHAR(32),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_invoice_customers_seller ON invoice_customers (seller_id);

CREATE TABLE invoice_counters (
    id          UUID PRIMARY KEY,
    seller_id   UUID NOT NULL,
    year        INT NOT NULL,
    last_value  BIGINT NOT NULL DEFAULT 0,
    UNIQUE (seller_id, year)
);

CREATE TABLE invoices (
    id                UUID PRIMARY KEY,
    seller_id         UUID NOT NULL,
    invoice_number    VARCHAR(32),
    customer_id       UUID REFERENCES invoice_customers (id),
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    subtotal_paise    BIGINT NOT NULL DEFAULT 0,
    discount_paise    BIGINT NOT NULL DEFAULT 0,
    tax_paise         BIGINT NOT NULL DEFAULT 0,
    total_paise       BIGINT NOT NULL DEFAULT 0,
    currency          VARCHAR(8) NOT NULL DEFAULT 'INR',

    issued_at         TIMESTAMPTZ,
    due_at            TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ
);
CREATE INDEX idx_invoices_seller_created ON invoices (seller_id, created_at DESC);
CREATE INDEX idx_invoices_seller_status_created ON invoices (seller_id, status, created_at DESC);
CREATE UNIQUE INDEX idx_invoices_number ON invoices (invoice_number) WHERE invoice_number IS NOT NULL;

CREATE TABLE invoice_items (
    id                    UUID PRIMARY KEY,
    invoice_id            UUID NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    item_type             VARCHAR(16) NOT NULL,

    product_id            UUID,
    variant_id            UUID,

    name_snapshot         VARCHAR(255) NOT NULL,
    sku_snapshot          VARCHAR(64),
    barcode_snapshot      VARCHAR(64),

    catalog_price_paise   BIGINT,
    unit_price_paise      BIGINT NOT NULL,
    quantity              INT NOT NULL,

    discount_paise        BIGINT NOT NULL DEFAULT 0,
    tax_rate              DOUBLE PRECISION NOT NULL DEFAULT 0,
    tax_paise             BIGINT NOT NULL DEFAULT 0,
    total_paise           BIGINT NOT NULL,

    price_override        BOOLEAN NOT NULL DEFAULT FALSE,
    override_reason       VARCHAR(255),

    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_invoice_items_invoice ON invoice_items (invoice_id);

CREATE TABLE invoice_pdfs (
    id            UUID PRIMARY KEY,
    invoice_id    UUID NOT NULL UNIQUE REFERENCES invoices (id) ON DELETE CASCADE,
    pdf_bytes     BYTEA NOT NULL,
    generated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE invoice_deliveries (
    id                   UUID PRIMARY KEY,
    invoice_id           UUID NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    channel              VARCHAR(16) NOT NULL,
    destination          VARCHAR(255) NOT NULL,
    status               VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    sent_at              TIMESTAMPTZ,
    delivered_at         TIMESTAMPTZ,
    failed_at            TIMESTAMPTZ,
    failure_reason       VARCHAR(500),
    provider_message_id  VARCHAR(255),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_invoice_deliveries_invoice ON invoice_deliveries (invoice_id, created_at DESC);
