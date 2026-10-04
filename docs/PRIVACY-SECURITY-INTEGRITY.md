# Privacy, Security and Integrity

## Privacy

AWi&k is local-first and has no network permission. Its Room database lives in app-private storage. Android backup remains disabled until an encrypted, user-controlled backup design exists.

## Financial integrity

Inventory quantities and authoritative currency projections use integer-backed fixed point.

Running Balance uses exact currency minor units:

```
balanceMinor = running SUM(debitMinor - creditMinor)
```

Balance is derived rather than stored, preventing stale downstream balances after backdated corrections.

## Posting integrity

DRAFT -> FINAL is one-way. A FINAL correction remains FINAL and increments revision.

Posted rows can be:
- VOIDed explicitly, preserving history while removing their financial effect; or
- REVERSED explicitly, preserving the original and generating an immutable counter-entry.

FINAL -> DRAFT is rejected at the repository boundary.

Inventory-owned rows are locked and cannot be voided/reversed from generic Khata controls.

## Analytical integrity

Official balance and pivot calculations include FINAL and REVERSED source rows. DRAFT and VOID are excluded. Reversal rows are FINAL and therefore included.

Search, UI sorting, and pagination do not change the running-balance input set.

## Audit integrity

Mutation metadata is HMAC-SHA-256 chained using a key generated in Android Keystore. Raw business rows are not duplicated into the audit table; canonical mutation material is hashed.

Void/reversal reasons are included in hashed audit material.

## Import integrity

CSV is untrusted interchange data:
- Khata imports always enter DRAFT
- imported State/Balance fields are ignored
- finalization revalidates required and typed fields
- Inventory import never overwrites an existing SKU
- opening stock is journaled through the normal stock adjustment path

## File access

Import/export uses Android's Storage Access Framework. No broad external-storage permission is requested.
