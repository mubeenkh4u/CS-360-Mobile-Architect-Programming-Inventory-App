# Data Model

## Dataset and columns

A dataset is a logical table. `LEDGER` is the canonical Khata dataset kind.

Runtime columns store:
- display name
- logical type
- semantic role
- position
- required/protected flags

The canonical ledger data columns are Date, Party, Particulars, Product, SKU, Quantity, Rate, Debit, Credit, Reference, and Stock Status.

**State and Balance are virtual/system fields, not editable dataset columns.**

## Row lifecycle

`data_rows` stores stable identity, timestamps, revision, status, origin, originRef, and isLocked.

Statuses:
- `DRAFT`
- `FINAL`
- `REVERSED`
- `VOID`

Origins:
- `MANUAL`
- `IMPORT`
- `STOCK_DOCUMENT`
- `REVERSAL`

Inventory-generated rows are FINAL + STOCK_DOCUMENT + locked.

A reversal row is FINAL + REVERSAL + locked and references its source row through `originRef`.

## Cells

Cells preserve:
- rawValue
- normalizedValue
- numericValue
- moneyMinorValue
- instantValue
- booleanValue

`moneyMinorValue` is the exact integer projection used by financial running-balance calculations. `numericValue` remains useful for generic analysis.

## Derived running Balance

Balance is not stored in `cells`. For each displayed row, a correlated SQLite query sums `debitMinor - creditMinor` over all earlier official rows for the same normalized Party, ordered by Date, createdAt, then rowId. This is compatible with the app's API 26 SQLite floor and avoids loading the full ledger into application memory.

Rows with no Party do not receive a derived balance. New finalized financial rows require Party.

## Void vs reversal

VOID changes the row status while preserving its cells and audit history. It is excluded from official balance/pivot calculations.

REVERSE:
1. marks the original FINAL row as REVERSED;
2. creates an immutable FINAL counter-entry dated today;
3. swaps Debit and Credit;
4. links the reversal to the source row;
5. leaves both rows in history.

The original and counter-entry therefore net to zero from the reversal date forward.

## Inventory domain

Products, stock documents, lines, and stock movements remain strongly typed. Physical stock is derived from the immutable movement journal.

## CSV

Khata export header uses `_AWi&k State`. `Balance` is exported after Credit. State and Balance are ignored on import; incoming rows are staged as DRAFT.

Inventory import accepts product master/opening stock only; derived stock states are not source-of-truth imports.

## Migrations

- v1 -> v2: inventory tables
- v2 -> v3: row lifecycle/origin/lock metadata
- v3 -> v4: exact `moneyMinorValue` projection for currency cells + index/backfill
