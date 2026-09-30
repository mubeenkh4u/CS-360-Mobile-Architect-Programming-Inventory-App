# Data Model

## Flexible data workbench

### Dataset

A logical table. `LEDGER` is the canonical Khata dataset kind.

### Column

Runtime metadata defines name, type, semantic role, position, required state and protected state.

Supported types include TEXT, INTEGER, DECIMAL, CURRENCY, PERCENTAGE, DATE, DATETIME, BOOLEAN and CATEGORY.

### Row / Cell

Rows hold identity/revision metadata. Cells preserve raw source text plus optional normalized, numeric, date/time and boolean projections.

## Inventory domain

### Product

`inventory_products` stores:

- SKU
- name
- unit
- reorder level
- optional default sale rate
- optional default purchase rate
- active state
- timestamps

### Stock Document

`stock_documents` represents SALE or PURCHASE workflows with one of:

- TENTATIVE
- COMMITTED
- CANCELLED

It stores party, reference, effective date, total, immediate payment and notes.

### Stock Document Line

`stock_document_lines` stores product, fixed-point quantity, fixed-point rate and line total. The model supports multiple lines per document even when a UI chooses to create a single-line transaction.

### Stock Movement

`stock_movements` is the committed physical stock journal.

A product's base/on-hand quantity is derived from the sum of its immutable movement deltas. There is no mutable `currentQuantity` field that can silently drift away from history.

### Fixed point

- `quantityMicros: Long` — six decimal places
- `unitRateMinor: Long` / `totalMinor: Long` — monetary minor units

This prevents business truth from depending on binary floating-point arithmetic.

## Ledger semantic integration

The canonical ledger ensures these semantic roles exist:

- DATE
- PARTY
- PARTICULARS
- PRODUCT
- SKU
- QUANTITY
- RATE
- DEBIT
- CREDIT
- REFERENCE
- STOCK_STATUS

Inventory uses roles to post data. User-added columns remain independent.

## Audit events

Application mutations generate chained audit metadata. Audit records contain hashes/metadata rather than full duplicated business rows.

## Migration

Database v2 adds the inventory product, document, line and movement tables while preserving every v1 dataset, row, cell, transformation and audit event.
