# Data Model

## Flexible data workbench

### Dataset

A logical table. `LEDGER` is the canonical Khata dataset kind.

### Column

Runtime metadata defines name, type, semantic role, position, required state and protected state.

Supported types include TEXT, INTEGER, DECIMAL, CURRENCY, PERCENTAGE, DATE, DATETIME, BOOLEAN and CATEGORY.

### Row

`data_rows` stores:

- stable ID
- dataset ID
- created/updated timestamps
- monotonically increasing `revision`
- `status`: DRAFT or FINAL
- `origin`: MANUAL, IMPORT or STOCK_DOCUMENT
- optional `originRef`
- `isLocked`

DRAFT rows may be incomplete. FINAL rows satisfy required/type validation. STOCK_DOCUMENT rows are locked because their source of truth is a committed inventory transaction.

### Cell

Cells preserve raw source text plus normalized, numeric, date/time and boolean projections.

## Inventory domain

### Product

`inventory_products` stores SKU, name, unit, reorder level, optional default sale/purchase rates, active state and timestamps.

### Stock Document

`stock_documents` represents SALE or PURCHASE workflows with TENTATIVE, COMMITTED or CANCELLED status.

### Stock Document Line

`stock_document_lines` stores product, fixed-point quantity, fixed-point rate and line total. The data model supports multiple lines per document.

### Stock Movement

`stock_movements` is the committed physical stock journal. Base/on-hand stock is derived from movement deltas; there is no mutable `currentQuantity` source of truth.

### Fixed point

- `quantityMicros: Long` — six decimal places
- monetary values — integer minor units

## Ledger semantic integration

The canonical ledger ensures semantic roles for Date, Party, Particulars, Product, SKU, Quantity, Rate, Debit, Credit, Reference and Stock Status.

Inventory-generated ledger rows are FINAL, STOCK_DOCUMENT-origin and locked.

## CSV interchange

Khata export writes `_Auwire Status` plus runtime columns. Import matches display names case-insensitively, creates unknown fields as TEXT, and stages imported rows as DRAFT.

Inventory export includes product master and stock snapshots. Import accepts product master plus Base Stock; reservation/projected fields are informational and are not imported.

## Audit events

Application mutations generate chained audit metadata. AuditWriter stores a SHA-256 hash of canonical event material rather than duplicating raw business rows. Manual row updates hash both before and after cell material.

## Migrations

- v1 → v2 adds inventory product, document, line and movement tables.
- v2 → v3 adds row lifecycle/origin/lock metadata and indices.

The v2 → v3 migration keeps existing rows FINAL. Existing rows with semantic `STOCK_STATUS=COMMITTED` are detected as STOCK_DOCUMENT-origin, locked, and associated with their Reference where present.
