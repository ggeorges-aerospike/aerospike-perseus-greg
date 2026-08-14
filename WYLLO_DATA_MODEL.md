# Wyllo feature platform — MongoDB to Aerospike

## Source

The feature platform stores one Mongo document per `(entity, feature)` pair. Cold features is
by far the largest collection:

```json
{"_id": {"$oid": "6a2c1147e6325a3e33575ab8"}, "entity_type": "bin",    "entity_id": "411111",             "feature_id": "bin_avg_tpv_3d",            "value": 152.34}
{"_id": {"$oid": "6a2c1147e6325a3e33575ab8"}, "entity_type": "bin_ip", "entity_id": "411111|203.0.113.7", "feature_id": "bin_ip_avg_amount_90d",     "value": 88.10}
{"_id": {"$oid": "6a2c1147e6325a3e33575ab8"}, "entity_type": "email",  "entity_id": "a3f9c2e1",           "feature_id": "email_days_since_last_txn", "value": 3.0}
```

The `_id` ObjectId is the **user id**, so all of a user's features share it.

## Target

**One record per user**, keyed by the ObjectId verbatim. **One bin per `entity_type`**, each a
`KEY_ORDERED` map of `entity_id` -> `KEY_ORDERED` map of `feature_id` -> value:

```
PK "6a2c1147e6325a3e33575ab8"
  bin:    { "411111": { "avg_tpv_3d": 152.34, "cnt_txn_7d": 12.0 },
            "555555": { "avg_tpv_3d": 4.10 } }
  bin_ip: { "411111|203.0.113.7": { "avg_amount_90d": 88.10 } }
  email:  { "a3f9c2e1": { "avg_days_since_last_txn_1d": 3.0 } }
```

Why this shape:

- **Bin per entity type** rather than one map with composite keys: Aerospike is schemaless, so a
  user with no email features simply has no `email` bin. A single entity type reads back with
  `client.get(ns, key, "bin_ip")` — no expression, no range scan, less on the wire. It also keeps
  bin-level XDR shipping available, so different pipelines can own different entity types without
  last-writer-wins conflicts.
- **`entity_id` as a nested level** because a user routinely has several entities of one type
  (multiple cards, multiple BIN×IP pairs). A flat `feature_id -> value` map per bin would let the
  second card silently overwrite the first, since map keys are unique.
- **`KEY_ORDERED` at both levels** so entries sort on write: `getByKey` is O(log n) and
  `getByKeyRange` / pagination stay available.
- **The `entity_type` prefix is stripped from `feature_id`** (`bin_avg_tpv_3d` -> bin/`avg_tpv_3d`)
  because the bin name already carries it. Reversing the migration means re-prefixing with the
  bin name.

## Constraints this model respects

| Constraint | How |
|---|---|
| Bin names <= 15 chars (hard limit) | Entity types are bin names; `TestSetup` throws at startup if one is too long |
| Nested CDTs are not auto-created | Writes use `CTX.mapKeyCreate`, not `CTX.mapKey`, so the first write for a new entity works |
| Records are rewritten whole on update (copy-on-write) | One record per user, not per user-plus-history; measured ~5.5 kB at 200 features |
| Per-record `transaction-pending-limit` (KEY_BUSY) | The generator spreads writes randomly across all users created so far rather than hammering a current user |

**Never key a bin by `entity_id`.** Bins are a per-namespace vocabulary, not a data dimension —
a bin per card would push bin-name cardinality into the millions. Bins for the handful of entity
*types*, map keys for the instances.

## Measured record size

300 writes × 20 features across 31 users on Aerospike EE 8.0.0.7:

```
objects=31  data_used_bytes=170016   ->  ~5.5 kB per user at ~195 features
```

That is **~28 bytes per feature** all-in (map key + double + CDT overhead) — roughly half what
naive key-bytes-plus-8 arithmetic predicts, because Aerospike's CDT encoding is compact. At 200
features a user record is small enough that copy-on-write on a single-feature update stays cheap.

## Workload

| threads.yaml key | Test | Unit of work |
|---|---|---|
| `featureWrite` | `FeatureWriteTest` | One entity's features upserted onto one user — a single-record `operate`, atomic without a transaction |
| `featureBatchWrite` | `FeatureBatchWriteTest` | `writeBatchSize` upserts across distinct users in one round trip |
| `featureRead` | `FeatureReadTest` | Whole user record, every entity type |
| `featureBinRead` | `FeatureBinReadTest` | One entity type — `get(key, "bin_ip")` |
| `featureEntityRead` | `FeatureEntityReadTest` | One entity's whole feature map — `getByKey` on the outer map |
| `featureVectorRead` | `FeatureVectorReadTest` | A named subset of features for one entity — the model-scoring read |
| `featureBatchRead` | `FeatureBatchReadTest` | `readBatchSize` user records in one round trip |

**For the 1M writes/sec target, drive `featureBatchWrite`.** At batch 100 the client needs ~10k
ops/sec rather than 1M. Note the TPS column counts *batches*, not records — multiply by the batch
size for record throughput. The same applies to `featureBatchRead`.

Comparing `featureRead` against `featureBinRead` and `featureVectorRead` sizes what the model
buys you: whole record vs one entity type vs the ten features inference actually wants.

### Reads need a writer running

Reads pick users from the range the writers have populated **in this process**, so run at least
one writer thread alongside them — otherwise the range is a single user and every read hits the
same record, which measures the rw-hash limit rather than the cluster.

### Expected hit rates

Measured over 500 trials after seeding 4,000 writes across 400 users:

| Read | Hit rate | Why not 100% |
|---|---|---|
| user read | 96% | The user universe grows as writes land, so the newest ids have not been written yet. `randomWrittenUser` already holds back the newest decile; the remainder is the sparse tail. |
| bin read | 87% | A user may not own that entity type yet. |
| entity read | 66% | A user may not own that *instance* of the type — real feature coverage is sparse. |
| vector read | 64% full, 0% partial | Same 1-in-3 sparsity as entity read. **Partial is zero**: whenever the entity exists, all requested names are present — which is the check that the deterministic id/name derivation is exact. |
| batch read | 98% | As user read. |

Treat these as the baseline. A materially lower number means something changed, not that the
harness is broken.

### Why the vector read is an expression, not a CTX

The obvious implementation is `MapOperation.getByKeyList(bin, names, KEY_VALUE, CTX.mapKey(entityId))`.
That **fails with error 26 ("Operation not applicable")** whenever the user does not own that
entity, because a CTX naming a missing path is an error rather than an empty result — and with
sparse coverage that is a third of reads. Feeding the inner map into the key-list lookup as an
expression and reading with `ExpReadFlags.EVAL_NO_FAIL` returns null on a missing path instead,
so an absent entity is a normal empty answer. This is worth knowing generally: **nested CTX reads
throw on missing paths where top-level bin reads quietly return nothing.**

## Knobs (`configuration.yaml`)

| Key | Default | Meaning |
|---|---|---|
| `wylloFeaturesPerEntity` | 20 | Features upserted per write |
| `wylloAvgWritesPerUser` | 10 | Writes before the user universe grows by one |
| `wylloBinUniverse` | 100000 | Distinct card BINs |
| `wylloMaxBinsPerUser` | 2 | `bin` instances per user |
| `wylloMaxBinIpsPerUser` | 5 | `bin_ip` instances per user |
| `wylloMaxEmailsPerUser` | 1 | `email` instances per user |
| `wylloBinWeight` / `wylloBinIpWeight` / `wylloEmailWeight` | 5 / 3 / 2 | Share of writes per entity type |
| `wylloVectorSize` | 10 | Features requested per model-scoring read |
| `wylloTtlSeconds` | 0 | 0 = never expire; cold features have no TTL today |
| `writeBatchSize` | 100 | Records per batch write |
| `readBatchSize` | 50 | Records per batch read |

Adding an entity type is a one-line change to the `specs` array in `TestSetup`.

## Generated data fidelity

- **User keys** are 24 hex chars with a realistic 4-byte ObjectId timestamp prefix; the counter
  occupies the low 8 bytes so keys stay unique. `perseusId` offsets the range so N Perseus
  clients never collide on a user.
- **Entity ids** match real byte lengths: BINs are 6 digits, `bin_ip` is `BIN|IPv4`, email is an
  8-hex hash. Key length lands in every map entry, so it drives record size.
- **Feature names** come from a 504-name vocabulary (`{agg}_{measure}_{window}`) whose lengths
  match the observed `feature_id`s. Successive names stride by 101 (coprime with 504) so one
  entity's 20 features span different measures and time windows instead of clustering in one.
- **Everything is derived from the user id** (`FeatureIds`): which entity instances a user owns
  and which features each carries. Reads reconstruct ids that exist without coordinating with the
  writers, and it models reality better than random ids — a user really does have a stable set of
  cards and emails, and the pipeline computes a stable feature set for each.

## Open questions

1. **Multiple entities per type** — confirmed as the reason for the nested level, but the real
   distribution (cards per user, BIN×IP pairs per user) is a guess. The defaults above are
   placeholders.
2. **Delimiter in `bin_ip`** — `|` is used here. `:` would be unsafe if `entity_id` can ever
   contain one (raw emails, IPv6).
3. **Predictions collection** is not modelled. It is the only unbounded collection and wants a
   TTL plus a relational sink; it is a separate set and a separate workload.
