# Architecture

## Layering

```
app
  -> feature:workspace (Khata UI)
  -> feature:inventory (Inventory UI)
  -> core:ui
       |
       v
core:data
  -> DatasetRepository
  -> InventoryRepository
  -> CSV transfer services
       |
       v
core:database
  -> Room / SQLite / DAOs
       |
       v
app-private database

core:model contains dependency-light domain contracts and fixed-point rules.
```

Feature UI never accesses a DAO directly.

## Posted-row lifecycle

User-managed rows follow a one-way posting model:

```
DRAFT -> FINAL
          |  \
          |   -> VOID
          |
          -> REVERSED + REVERSAL row
```

- A DRAFT may be incomplete and may stay DRAFT until ready.
- Finalization validates required and typed fields.
- A FINAL correction updates the same row, increments revision, and stays FINAL.
- FINAL -> DRAFT is rejected at the repository boundary.
- VOID preserves the row/audit trail but removes it from official analytics.
- REVERSE marks the original REVERSED and creates a locked FINAL counter-entry with swapped Debit/Credit.
- Inventory rows remain system-owned and locked.

All lifecycle mutations use optimistic revision matching and Room transactions.

## Running-balance boundary

Balance is **derived**, not stored as a mutable cell.

For each official ledger row:

```
delta = debitMinor - creditMinor
balance = running SUM(delta) partitioned by normalized Party
```

Accounting order is:

1. Date
2. createdAt
3. row ID

The running window is calculated across the complete official ledger before UI paging/filtering. Therefore search, sorting, and moving between pages never alter historical balances.

Official balance membership:
- FINAL: included
- REVERSED: included
- REVERSAL row: included because it is FINAL
- DRAFT: excluded
- VOID: excluded

## Exact money

Generic analytical columns still retain their numeric projection, but CURRENCY cells also store `moneyMinorValue: Long?`. Database v4 backfills existing currency cells and all new currency parsing uses exact BigDecimal -> minor-unit conversion.

## Inventory transaction boundary

Committing a sale/purchase remains one Room transaction:

1. validate document/products
2. validate fixed-point quantity/money
3. validate sale availability
4. append stock movements
5. ensure canonical ledger schema
6. append locked FINAL Khata rows
7. append payment row when applicable
8. mark document COMMITTED
9. append chained audit events

A generic Khata edit cannot mutate a stock-owned row.

## CSV boundary

Storage Access Framework provides scoped file access.

Khata:
- export includes display State and derived Balance
- import ignores State/Balance
- imported rows always become DRAFT
- unknown headers become TEXT columns

Inventory:
- import creates new SKUs only
- Base Stock is journaled through the inventory repository
- tentative/reserved/projected quantities are export-only derived information
