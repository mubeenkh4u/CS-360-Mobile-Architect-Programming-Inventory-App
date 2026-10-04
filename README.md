# AWi&k

AWi&k is a **local-first Khata + inventory platform**. Inventory owns physical-stock truth; Khata owns party-account history and flexible analysis. Committed inventory transactions post into Khata atomically.

The original SNHU submission remains untouched under `Project-Files/`.

## Current capabilities

### Khata

- Dynamic runtime columns and typed cells
- DRAFT -> FINAL one-way posting lifecycle
- FINAL corrections remain FINAL and increment the row revision
- Explicit VOID and REVERSAL accounting actions
- Inventory-generated accounting rows are system-locked
- Derived per-party running Balance shown after Credit
- Balance uses exact integer minor units (paisa), not floating-point accumulation
- Balance is chronological and independent of search/sort/paging
- DRAFT and VOID rows do not affect official balance/pivot totals
- REVERSED source rows remain historical and are offset by immutable reversal rows
- Search, typed sorting, paging, cleaning of draft data, and pivot analysis
- CSV import/export through Android's Storage Access Framework

### Inventory

- Product master with SKU, unit, reorder level, and default rates
- Immutable committed stock movement journal
- Base/on-hand, reserved outgoing, tentative incoming, available-to-promise, and projected quantities
- Sale and purchase documents
- Tentative commit/cancel workflow
- Atomic stock + Khata posting
- Immediate payment posting
- CSV product/base-stock import and stock snapshot export

## Row lifecycle

```
DRAFT --Finalize--> FINAL --Correction--> FINAL (revision + 1)
                       |
                       +--Void-------> VOID
                       |
                       +--Reverse----> REVERSED + immutable REVERSAL row
```

- **DRAFT**: incomplete/staging; editable; excluded from official accounting.
- **FINAL**: posted/official; editable only as a correction that remains FINAL.
- **VOID**: preserved in history but excluded from balance and pivot totals.
- **REVERSED**: original posted row retained in official history.
- **REVERSAL**: immutable counter-entry generated from a user-managed FINAL row.
- **LOCKED**: display state for system-owned Inventory postings; they must be corrected through their source workflow.

FINAL rows never return to DRAFT.

## Running balance

Balance is a virtual, read-only field:

```
Balance = previous party balance + Debit - Credit
```

It is partitioned by normalized Party and ordered by:

1. accounting Date
2. row creation time
3. row ID

Positive balances display **DR** (you will receive). Negative balances display **CR** (you will pay). Zero is settled.

The balance is derived at read time and is never imported or directly edited, so backdated corrections automatically recalculate later balances without rewriting stored ledger rows.

## CSV transfer

Khata export includes `_AWi&k State` and derived `Balance`. On import, both are ignored and every non-empty row is staged as DRAFT. Unknown non-system headers become TEXT columns.

Inventory import requires SKU, Name and Unit. It can also import Reorder Level, default rates, and Base Stock. Existing SKUs are skipped; derived/tentative stock values are not recreated from CSV.

## Precision and integrity

- Quantity source of truth: integer micros (1 unit = 1,000,000)
- Money source of truth for ledger balance: integer minor units (1 PKR = 100 paisa)
- Explicit Room migrations; no destructive fallback
- Optimistic row revisions
- HMAC-SHA-256 chained audit metadata using Android Keystore
- No INTERNET permission
- No broad storage permission
- Android backup disabled
- Screenshot protection enabled by default

See `docs/` for architecture, data model, inventory integration, signing, and security details.
