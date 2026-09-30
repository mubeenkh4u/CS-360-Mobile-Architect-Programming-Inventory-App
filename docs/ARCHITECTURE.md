# Architecture

## Principles

IAM Khata follows a layered, local-first design:

```
app
 ├─ composition root / feature flags / platform security
 └─ feature:workspace
       ├─ Compose UI
       └─ ViewModel
              ↓
          core:data
       repository + mutation/audit rules
              ↓
        core:database
       Room entities + DAOs
              ↓
          SQLite

core:model is dependency-light and shared upward.
```

The UI never talks directly to a DAO. Database implementation details are hidden behind `DatasetRepository`.

## Why the tabular model is separate from accounting

A rigid 50-column ledger entity would make every new field a database migration. Instead, datasets have runtime schemas:

- Dataset
- Column metadata
- Row identity/revision
- Typed cell values

Accounting-specific meaning is metadata (`ColumnRole`) rather than hard-coded table shape. This lets a ledger add columns such as vehicle, warehouse, salesman or commission without changing application binaries.

## Heavy-data strategy

The first storage engine is indexed SQLite through Room. Cells retain the original text and typed projections, allowing numeric/date operations without reparsing every record.

Analytics is accessed through repository contracts and parameterized raw queries. A future columnar/OLAP engine can implement the same higher-level contract without rewriting the UI or canonical ledger store.

## Feature isolation

Capabilities are surfaced through `WorkspaceFeatures`. Disabling cleaning or pivoting removes those actions while leaving dataset browsing and entry usable. Future import, join, formula and sync features should follow the same pattern.

## Mutation rules

All source-of-truth writes:

1. validate at the repository boundary;
2. run inside a Room transaction;
3. update row revisions when applicable;
4. append a tamper-evident audit event;
5. avoid logging raw business data.

Derived views and pivots do not rewrite source data.
