# cloud-itonami-isco-2151

Open Business Blueprint for **ISCO-08 2151**: Electrical Engineers — an ISCO
**Wave 1 (design & governance)** occupation per ADR-2607121000. This
is the SECOND wave-1 blueprint batch (21xx engineering design
professions): the design/analysis work is cognitive; physical
execution remains robotics-gated and out of the actor's scope.

**Maturity: `:implemented`** — ElectricalEngineersAdvisor ⊣
ElectricalEngineersGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
44 tests / 89 assertions green.

The circuit HARD invariants — arithmetic and equality, not field
negotiation:

1. **Ampacity margin** — the proposed load current must not exceed the
   circuit's registered ampacity rating.
2. **Voltage-class match** — the proposed equipment's voltage class
   must equal the circuit's registered voltage class (no mismatch, no
   substitution).

Also HARD: unregistered/foreign circuit, unregistered organization,
non-`:propose` effect. Escalations (always human sign-off):
`:energize-circuit` (live energization), low confidence (< 0.6).

Those two are only meaningful if the proposal is one the actor may
consider at all, so they are reached through two prior gates:

3. **Closed op vocabulary** (`electeng.operation`) — an op outside
   `supported` is refused as `:unsupported-op`, and an op in
   `reserved` — protection defeat, live work, earthing changes — is
   refused as `:reserved-op`. The two are separate refusals on
   purpose: the first sends an operator to look for a typo, the
   second to the qualified person on site who is allowed to do it.
4. **Well-formedness** — a supported op must carry the fields its
   checks need (`:approve-load` needs `:circuit-id :load
   :voltage-class`), in a usable form, and a registered circuit must
   carry a usable `:ampacity` and a known `:voltage-class`
   (`:circuit-not-ratable`) before any arithmetic is done against it.

Every commit is recorded through `electeng.ledger`, whose entries name
the `:authorisation` that permitted them — `:governor-clear`,
`:human-sign-off` or `:governor-hold`. An entry that does not name one
is refused at construction, so the audit trail can answer the question
it is kept for: whether the sign-off this README promises for live
energization was actually given.

### What these gates were measured to admit before they existed

Measured on `f509ac8`, the tip before this work. The governor applied
its two HARD checks only when `(= :approve-load op)` and its
escalation only when `(= :energize-circuit op)`, so every other op
fell through both:

| proposal | verdict then | verdict now |
|---|---|---|
| `{:op :disable-rcd-protection}` | `:ok? true` — and the actor **committed a record** | `:reserved-op`, nothing committed |
| `{:op :demolish-substation}` | `:ok? true` | `:unsupported-op` |
| `{:op "approve-load"}` (string) | `:ok? true` | `:unsupported-op` |
| `{:load nil}` / `{:voltage-class nil}` | `:ok? true` | `:incomplete-proposal` |
| `{:load "80"}` / `{:load -50}` / `{:load 0}` | `:ok? true` | `:ill-formed-field` |
| `{:confidence 2.5}` | `:ok? true` (above any floor) | `:ill-formed-field` |
| circuit registered without `:ampacity` | **NullPointerException** | `:circuit-not-ratable` |
| circuit with `:ampacity "100"` | **ClassCastException** | `:circuit-not-ratable` |
| auto-commit vs. `approve!`-resumed commit | byte-identical ledger entries | `:governor-clear` vs `:human-sign-off` |

A crash is not a refusal: it produces no verdict, no violation and no
ledger entry, so the one thing the actor exists to do does not happen.

The refusals are held in place by mutation tests
(`scripts/maturity-loop/mutations.edn` in the superproject), not by
these paragraphs. Each of the eight surgical mutations was watched
going red. One of them leaves `:disable-rcd-protection` refused but
relabels it `:unsupported-op` — a test asserting only that the actor
refused stays green under it, which is why every test here pins the
name of the rule it claims to be about.


AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
