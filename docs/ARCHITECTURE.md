# Architecture

## Layering

```
app
 ├─ composition root
 ├─ runtime feature flags / business policy
 └─ top-level Khata / Inventory navigation
       │
       ├─ feature:workspace
       │    └─ GUI tabular workbench
       │
       └─ feature:inventory
            └─ inventory workflow UI
                    ↓
                core:data
       ┌────────────┴────────────┐
       │                         │
DatasetRepository        InventoryRepository
       │                         │
       └──────────┬──────────────┘
                  ↓
             core:database
          Room / SQLite / DAOs
                  ↓
            app-private DB

core:model contains dependency-light domain contracts and fixed-point rules.
```

The UI never accesses a DAO directly.

## Two complementary data models

The workbench remains schema-flexible: users can add business-specific columns without database migrations.

Inventory is intentionally more strongly typed because stock quantity, document status and money require stricter invariants than an arbitrary table.

The two domains meet through semantic ledger roles. Inventory posts to roles such as PARTY, PRODUCT, QUANTITY, DEBIT and CREDIT rather than assuming fixed column IDs or screen positions.

## Transaction boundary

Committing a sale/purchase is one Room transaction:

1. Validate the tentative document.
2. Validate products and fixed-point values.
3. For sales, validate committed stock after protecting other reservations.
4. Append immutable stock movements.
5. Ensure the canonical ledger semantic columns exist.
6. Append one Khata row per document line.
7. Append an immediate-payment row when applicable.
8. Mark the stock document COMMITTED.
9. Append audit-chain events.

Any exception rolls all steps back.

## Tentative workflow

Tentative documents never mutate physical/base stock and never post official accounting rows.

They are intentionally visible in stock calculations:

- tentative sale → reserved outgoing;
- tentative purchase → projected incoming.

This allows operational work to continue before fulfillment while keeping committed stock and accounting truthful.

## Feature isolation

Build/runtime flags currently include:

- cleaning
- pivot
- inventory
- tentative stock
- negative committed stock policy
- screenshot protection
- import/export (reserved)
- cloud sync (reserved)

Disabling a feature does not invalidate the canonical dataset or stock journal.
