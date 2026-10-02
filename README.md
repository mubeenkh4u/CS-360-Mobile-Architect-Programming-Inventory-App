# Auwire

Auwire is a **local-first ledger + inventory data platform**. The Khata remains a flexible GUI-driven table, while inventory is a strongly governed business domain that posts into that ledger atomically.

The original SNHU submission remains untouched under `Project-Files/`.

## Current capabilities

### Khata / data workbench

- Dynamic runtime columns and typed cells
- Wide, horizontally scrollable ledger grid
- Paged row loading
- Search and typed sorting
- GUI cleaning operations
- GUI pivot builder with SUM, COUNT, AVERAGE, MIN and MAX
- Extensible custom columns without Room schema changes

### Inventory

- Product master with SKU, unit and reorder level
- Default sale and purchase rates
- Immutable committed stock movement journal
- Base/on-hand stock derived from committed movements
- Manual stock-in and stock-out adjustments
- Sale and purchase documents
- Tentative sale reservations
- Tentative purchase incoming stock
- Available-to-promise and projected quantities
- Tentative document commit/cancel workflow
- Stock shortage validation before committing a sale
- Atomic inventory + Khata posting
- Immediate-payment posting alongside sales/purchases
- Explicit Room v1 → v2 migration

## Stock states

For each product the application exposes:

```
Base / On Hand
    = sum(committed stock movements)

Reserved Outgoing
    = quantities in tentative SALE documents

Tentative Incoming
    = quantities in tentative PURCHASE documents

Available To Promise
    = Base - Reserved Outgoing

Projected
    = Available To Promise + Tentative Incoming
```

A tentative transaction can therefore continue a workflow without pretending that physical stock already moved. Tentative documents do **not** post to the official Khata. Committing a document converts it into physical stock movements and ledger rows inside one database transaction.

## Khata integration

Committed stock documents write semantic ledger fields by role rather than hard-coded column IDs:

- Date
- Party
- Particulars
- Product
- SKU
- Quantity
- Rate
- Debit
- Credit
- Reference
- Stock Status

A committed sale decreases base stock and debits the party ledger. A committed purchase increases base stock and credits the supplier ledger. Immediate payments generate the matching counter-entry.

If stock movement or ledger posting fails, the Room transaction rolls the whole commit back.

## Financial and quantity precision

Source-of-truth stock quantities and monetary amounts do not use binary floating point:

- Quantity: fixed point, 1 unit = 1,000,000 micros
- Money: integer minor units, 1 PKR = 100 paisa

The flexible analytical workbench may maintain numeric projections for querying, but inventory/accounting source records remain integer-backed.

## Security and integrity

- No INTERNET permission
- No broad storage permission
- App-private database
- Android backup disabled
- Cleartext traffic disabled
- Screenshot/screen-recording protection enabled by default
- Room transactions for multi-record business commits
- Explicit non-destructive migrations
- Append-only application audit events
- HMAC-SHA-256 audit chain with Android Keystore key
- No hard-coded production secrets
- Feature flags for independently disabling capabilities

See:

- [Architecture](docs/ARCHITECTURE.md)
- [Data model](docs/DATA-MODEL.md)
- [Inventory + Khata integration](docs/INVENTORY-KHATA-INTEGRATION.md)
- [Privacy, security and integrity](docs/PRIVACY-SECURITY-INTEGRITY.md)
- [Security policy](SECURITY.md)
