# Inventory + Khata Integration

## Core invariants

1. Physical/base stock changes only through committed stock movements.
2. Tentative documents never alter physical stock.
3. Tentative documents never post official Khata rows.
4. A committed sale cannot consume stock reserved for other tentative sales unless the explicit negative-stock policy is enabled.
5. A committed sale/purchase and all of its generated Khata rows succeed or fail together.
6. Committed document lines are represented in the ledger with a shared reference for traceability.
7. Business money and stock quantities use fixed-point integers.

## Sale

### Tentative sale

- reserves outgoing quantity;
- reduces available-to-promise;
- leaves base/on-hand untouched;
- creates no debit/credit row.

### Committed sale

- creates negative `SALE_ISSUE` stock movements;
- posts each line as a Khata debit;
- if money is received immediately, posts a Khata credit;
- marks the stock document COMMITTED.

## Purchase

### Tentative purchase

- increases tentative incoming/projected quantity;
- leaves base/on-hand untouched;
- creates no debit/credit row.

### Committed purchase

- creates positive `PURCHASE_RECEIPT` stock movements;
- posts each line as a supplier Khata credit;
- if money is paid immediately, posts a Khata debit;
- marks the stock document COMMITTED.

## Base-stock adjustment

Manual adjustments create immutable `ADJUSTMENT_IN` or `ADJUSTMENT_OUT` movements. They are audited but do not fabricate a commercial sale/purchase ledger entry.

## Shortage workflow

A tentative sale may be saved even when physical stock is insufficient. This supports quoting, order taking and planned fulfillment.

Commit remains blocked until:

```
on hand - reservations from other tentative sales >= requested quantity
```

unless the explicit negative-committed-stock policy is enabled.

This separates workflow continuity from financial/inventory truth.
