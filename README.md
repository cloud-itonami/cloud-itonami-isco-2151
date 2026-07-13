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
13 tests / 27 assertions green.

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

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
