# Data Model

## Dataset

A dataset is a logical table. `LEDGER` is the default dataset kind; `TABLE` supports general business data.

## Column

Columns are runtime metadata, not Java/Kotlin fields.

Supported types:

- TEXT
- INTEGER
- DECIMAL
- CURRENCY
- PERCENTAGE
- DATE
- DATETIME
- BOOLEAN
- CATEGORY
- FORMULA (reserved for the formula feature)

A column can also carry a semantic `ColumnRole` such as DATE, PARTICULARS, QUANTITY, RATE, DEBIT or CREDIT.

## Row

Rows contain identity, timestamps and a monotonically increasing revision. Business values live in cells.

## Cell

Each cell stores:

- `rawValue`: display/source value;
- `normalizedValue`: search/group key;
- `numericValue`: numeric projection when applicable;
- `instantValue`: epoch-millisecond projection for dates;
- `booleanValue`: boolean projection when applicable.

This preserves source fidelity while keeping filters, sorts and aggregates typed.

## Audit event

Audit records never need to duplicate entire sensitive rows. They record mutation metadata and payload hashes. Events are chained with an HMAC whose key is held by Android Keystore.

## Transformation event

A transformation records the operation type, affected column and affected-row count. This is the foundation for a future reversible transformation pipeline.
