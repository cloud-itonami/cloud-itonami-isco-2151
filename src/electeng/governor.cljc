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
    3. op vocabulary     — the op must be in `electeng.operation/supported`
                           (an unknown op) and must not be in
                           `reserved` (an act outside this actor's
                           authority). See that namespace for why the
                           two are distinct refusals.
    4. well-formedness   — a supported op's proposal must carry the
                           fields its checks need, in a usable form.
    5. circuit basis     — an approval must cite a REGISTERED circuit
                           belonging to this client.
    6. ratable circuit   — that circuit must carry a usable :ampacity
                           and a known :voltage-class. Before this
                           check, a circuit registered without them
                           made `check` THROW rather than refuse.
    7. ampacity margin   — the proposed load current must not exceed
                           the circuit's registered :ampacity.
    8. voltage-class match — the proposed equipment's voltage class
                           must equal the circuit's registered
                           :voltage-class (no mismatch, no
                           substitution).
  ESCALATION invariants (:escalate? true, human sign-off):
    9. an op whose vocabulary entry declares :escalates? true
       (:energize-circuit — live energization).
   10. low confidence (< `confidence-floor`).

  Ordering matters. 3 and 4 run BEFORE 7 and 8, because 7 and 8 were
  previously reachable only for `(= :approve-load op)` proposals that
  volunteered their numbers — so both the vocabulary and the numbers
  have to be established before the arithmetic means anything."
  (:require [electeng.store :as store]
            [electeng.operation :as operation]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record c]
  (let [{:keys [op load voltage-class]} proposal
        {:keys [status reason]} (operation/classify op)
        supported? (= :supported status)
        ;; Only a supported op has a shape to be well-formed against, and
        ;; only a well-formed one has numbers worth comparing. Guarding
        ;; the later checks on these is what stops an unstated load from
        ;; skipping the ampacity margin instead of failing it.
        shape (when supported? (operation/malformed proposal))
        well-formed? (and supported? (empty? shape))
        approve? (and well-formed? (= :approve-load op))
        ;; A registered circuit the governor cannot read is a refusal,
        ;; not an exception. Computed only once the op is well formed so
        ;; that the operator is told about one problem at a time.
        ratable (when (and approve? c) (operation/ratable-fault c))]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (= :reserved status)
      (conj {:rule :reserved-op
             :detail (str (pr-str op) " はこの actor の権限外: " reason)})

      (= :unsupported status)
      (conj {:rule :unsupported-op
             :detail (str (pr-str op) " は未知の op（既知: "
                          (pr-str (sort (keys operation/supported)))
                          "）— 語彙の外にある op は既定で拒否する")})

      :always (into (or shape []))

      (and approve? (nil? c))
      (conj {:rule :unknown-circuit :detail "未登録 circuit への負荷承認は不可"})

      (and approve? c (not= (:client-id c) (:client-id request)))
      (conj {:rule :circuit-wrong-client :detail "circuit が別 client のもの"})

      ratable
      (conj {:rule :circuit-not-ratable :detail ratable})

      (and approve? c (not ratable) (> load (:ampacity c)))
      (conj {:rule :ampacity-exceeded
             :detail (str "負荷電流 " load "A > 登録済み ampacity "
                          (:ampacity c) "A（電流算術は現場での交渉対象ではない）")})

      (and approve? c (not ratable) (not= voltage-class (:voltage-class c)))
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
        conf (:confidence proposal)
        ;; A non-numeric confidence is caught as :ill-formed-field above;
        ;; treating it as 0.0 here would compare it and throw first.
        low? (or (not (number? conf)) (< conf confidence-floor))
        risky-op? (operation/escalates? (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence (if (number? conf) conf 0.0)
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
