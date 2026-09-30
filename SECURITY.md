# Security Policy

IAM Khata treats ledger data as sensitive business information.

## Defaults

- Data is stored only in Android app-private storage.
- The application does not request network access.
- Android backup is disabled.
- Cleartext traffic is disabled.
- Screenshot/screen-recording protection is enabled by default.
- Mutations generate append-only audit records.
- Audit records are HMAC chained with a key generated in Android Keystore.
- Source code contains no API keys, passwords or production secrets.

## Reporting a vulnerability

Do not open a public issue containing customer data, credentials or exploit details. Report security-sensitive findings privately to the repository owner.

## Design rules

1. Do not log ledger values, credentials or imported file contents.
2. Do not add destructive Room migrations.
3. Do not add broad storage or network permissions when a scoped Android API can satisfy the requirement.
4. Validate and type imported data before committing it to canonical tables.
5. Keep analytical/derived output separate from source-of-truth financial records.
6. Any future cloud synchronization must use authenticated TLS, least-privilege authorization, conflict detection and server-side auditability.
