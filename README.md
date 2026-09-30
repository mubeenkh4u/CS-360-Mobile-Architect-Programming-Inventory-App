# IAM Khata Data Workbench

This branch replaces the earlier Khata prototype with a **local-first, GUI-driven tabular data platform** for ledgers and other business datasets.

The design goal is not "Pandas commands on Android." The goal is to expose the same class of operations—dynamic columns, filtering, sorting, cleaning, grouping, aggregation, pivoting and consolidation—through a safe visual interface while preserving accounting integrity.

## Current foundation

- Kotlin + Jetpack Compose
- Multi-module architecture with explicit dependency boundaries
- Room/SQLite transactional storage
- Dynamic schemas: datasets, columns, rows and typed cells
- Typed cell projections for text, number, date and boolean operations
- GUI data grid with horizontal scrolling
- Runtime column creation
- Runtime row entry
- Search/filter and column sorting
- GUI cleaning operations
- GUI pivot/group aggregation
- Append-only audit events chained with a Keystore-backed HMAC
- Feature flags so capabilities can be disabled without breaking the core ledger
- No INTERNET permission
- App-private database storage
- Android backup disabled for financial data
- Cleartext traffic disabled
- Screenshot protection enabled by default
- CI builds/tests and uploads a debug APK

The original SNHU submission remains untouched under `Project-Files/`.

## Architecture

See:

- [Architecture](docs/ARCHITECTURE.md)
- [Data model](docs/DATA-MODEL.md)
- [Privacy, security and integrity](docs/PRIVACY-SECURITY-INTEGRITY.md)
- [Security policy](SECURITY.md)

## Scope

This commit establishes the extensible data engine and its first working GUI. Import/export, joins, multi-table consolidation, formula columns, saved transformation pipelines, encrypted portable backups and cloud synchronization are intentionally separate features. They can be added behind interfaces and feature flags without changing the core data contract or making the ledger unusable.
