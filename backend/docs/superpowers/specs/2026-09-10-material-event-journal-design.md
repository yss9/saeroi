# Material Event Journal Design

## Goal

Record every material-related business milestone as an immutable, chronological event without replacing the existing material, balance, lock, lineage, trade, processing, production, evidence, or blockchain tables.

## Source of truth

Existing domain tables remain authoritative. `material_event` is an append-only audit and query model. A failed event insert rolls back the surrounding business transaction, so the domain change and its trace cannot diverge.

## Event model

Each event belongs to one material and contains an event type, occurrence time, optional actor, optional business reference, optional related material, optional quantity, a human-readable detail, and a unique idempotency key. Rows have no update or delete API.

The event types cover:

- material registration and derivation;
- on-chain minting and source verification;
- marketplace listing and cancellation;
- trade creation, payment, cancellation, shipment, receipt, and ownership transfer;
- processing request creation/termination, quote submission/selection, job payment/expiry;
- production input reservation/removal, batch cancellation, material consumption, and scrap creation;
- evidence attachment and deletion.

`reference_type` and `reference_id` link an event to its detailed domain record. `related_material_id` links parent and child events without replacing `material_lineage`. `quantity` is expressed in grams and is nullable for events without a meaningful quantity.

## Query API

`GET /api/materials/{materialId}/events` returns events ordered by `occurred_at`, then `id`, ascending. It accepts bounded `page` and `size` parameters. Authentication follows the existing material detail and lineage endpoints.

## Existing data

The migration inserts one `MATERIAL_HISTORY_IMPORTED` baseline event for each material that predates the journal. It describes the state at migration time and does not invent unavailable historical timestamps or actors.

## Consistency and idempotency

Callers generate deterministic idempotency keys from the event type, material ID, and domain record ID. A unique database constraint prevents retries from duplicating events. Event recording runs in the caller's existing transaction.

## Non-goals

- Replacing domain tables with event sourcing.
- Reconstructing exact historical events that were never recorded.
- Replacing `material_lineage` traversal.
- Publishing events to an external broker.

## Verification

Tests cover immutable event construction, persistence ordering and uniqueness, authenticated timeline lookup, baseline migration shape, and representative end-to-end event recording for material, trade, processing, production, evidence, and blockchain flows.
