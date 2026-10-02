# Architecture

## Layering

```
app
 ├─ composition root
 ├─ runtime feature flags / business policy
 ├─ persistent appearance preference
 └─ top-level Khata / Inventory navigation
       │
       ├─ feature:workspace
       │    └─ GUI tabular workbench + CSV file picker
       │
       ├─ feature:inventory
       │    └─ inventory workflow UI + CSV file picker
       │
       └─ core:ui
            └─ shared Auwire top bar / drawer / appearance controls
                    ↓
                core:data
       ┌────────────┴────────────┐
       │                         │
DatasetRepository        InventoryRepository
       │                         │
       ├─ DatasetCsvTransfer     ├─ InventoryCsvTransfer
       │                         │
       └──────────┬──────────────┘
                  ↓
             core:database
          Room / SQLite / DAOs
                  ↓
            app-private DB

core:model contains dependency-light domain contracts and fixed-point rules.
```

Feature UI never accesses a DAO directly.

## Editable row lifecycle

Manual and imported workbench rows use:

```
DRAFT → edit/review → FINAL
          ↑          │
          └── edit ──┘
```

Draft rows may be incomplete or temporarily contain values that do not yet parse to their declared type. Finalization validates required fields and typed values.

Updates use optimistic revision matching. A stale editor receives a conflict instead of overwriting a newer row. Each successful update increments the revision and appends a chained audit event.

Rows produced from committed stock documents are `STOCK_DOCUMENT` origin and `isLocked=true`. Generic table editing refuses those rows, preserving the stock/accounting transaction boundary.

## Two complementary data models

The workbench remains schema-flexible: users can add business-specific columns without database migrations.

Inventory is strongly typed because stock quantity, document status and money require stricter invariants than an arbitrary table.

The domains meet through semantic ledger roles rather than hard-coded column IDs.

## Transaction boundary

Committing a sale/purchase is one Room transaction:

1. Validate the document and products.
2. Validate fixed-point quantities and money.
3. For sales, protect stock reserved by other tentative sales.
4. Append immutable stock movements.
5. Ensure canonical ledger semantic columns exist.
6. Append locked FINAL Khata rows.
7. Append an immediate-payment row when applicable.
8. Mark the stock document COMMITTED.
9. Append chained audit events.

Any exception rolls all steps back.

## File transfer boundary

CSV import/export uses Android's Storage Access Framework. The app receives a user-selected document URI and reads/writes only that document; broad external-storage permission is not required.

CSV is staging/interchange, not a trusted database backup:
- Khata imports become DRAFT rows.
- Unknown Khata headers become TEXT columns.
- Inventory imports create new SKUs only.
- Opening/base stock is journaled through the normal inventory repository.
- Tentative stock projections are never recreated from CSV.

## Analytics boundary

Drafts are visible and editable in the grid, but pivot aggregation reads FINAL rows only so incomplete work cannot alter official analytical totals.

## Feature isolation

Build/runtime flags currently include:
- cleaning
- pivot
- inventory
- tentative stock
- negative committed stock policy
- screenshot protection
- import/export
- cloud sync (reserved)

Disabling a feature does not invalidate the canonical dataset or stock journal.
