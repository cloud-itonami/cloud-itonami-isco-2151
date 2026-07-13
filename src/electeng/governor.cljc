(ns electeng.governor
  "ElectricalEngineersGovernor — the independent safety/traceability
  layer for the ISCO-08 2151 community electrical engineers actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section).
  Modeled on cloud-itonami-isco-4311's bookkeeping.governor. Electrical
  twist: a proposed load's ampacity margin is arithmetic comparison
  against the registered circuit rating, and equipment either matches
  the circuit's registered voltage class or it does not — neither is
  negotiable in the field.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. circuit basis     — an approval must cite a REGISTERED circuit
                           belonging to this client.
    4. ampacity margin   — the proposed load current must not exceed
                           the circuit's registered :ampacity.
    5. voltage-class match — the proposed equipment's voltage class
                           must equal the circuit's registered
                           :voltage-class (no mismatch, no
                           substitution).
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :energize-circuit (live energization).
    7. low confidence (< `confidence-floor`)."
  (:require [electeng.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record c]
  (let [{:keys [op load voltage-class]} proposal
        approve? (= :approve-load op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and approve? (nil? c))
      (conj {:rule :unknown-circuit :detail "未登録 circuit への負荷承認は不可"})

      (and approve? c (not= (:client-id c) (:client-id request)))
      (conj {:rule :circuit-wrong-client :detail "circuit が別 client のもの"})

      (and approve? c (number? load) (> load (:ampacity c)))
      (conj {:rule :ampacity-exceeded
             :detail (str "負荷電流 " load "A > 登録済み ampacity "
                          (:ampacity c) "A（電流算術は現場での交渉対象ではない）")})

      (and approve? c voltage-class (not= voltage-class (:voltage-class c)))
      (conj {:rule :voltage-class-mismatch
             :detail (str "機器電圧クラス " voltage-class " != circuit 登録クラス "
                          (:voltage-class c) "（不一致は代替不可）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `electeng.store/Store`. Pure — never mutates
  the store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        c (some->> (:circuit-id proposal) (store/circuit store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record c)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :energize-circuit (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
