(ns electeng.operation
  "The op vocabulary of the ISCO-08 2151 electrical engineers actor,
  stated once, as an ALLOWLIST.

  Measured on f509ac8, before this namespace existed: the governor
  applied its two HARD checks only when `(= :approve-load op)` and its
  escalation only when `(= :energize-circuit op)`. Every other op in
  the world therefore fell through both and returned
  `{:ok? true :violations []}` — `:disable-rcd-protection` among them,
  and the full actor committed a record for it. An open vocabulary is
  not a permissive policy, it is the absence of one.

  Two maps, not one, because the two refusals are not the same refusal:

    `supported`  what this actor may propose. An op outside it is a
                 VOCABULARY error — someone misspelled an op, or asked
                 for something this actor was never built to do.
    `reserved`   acts that are real electrical engineering but are NOT
                 this actor's to propose. An op inside it is an
                 AUTHORITY boundary — the README's premise is that the
                 design/analysis work is cognitive and physical
                 execution stays with a qualified person on site.

  An operator told `unsupported-op` goes looking for a typo. An
  operator told `reserved-op` goes looking for the licensed electrician
  who is allowed to do it. Collapsing them sends half of them to the
  wrong place.

  A supported op also declares the fields a proposal MUST carry
  (`:requires`). Measured on the same commit: `{:load nil}` and
  `{:voltage-class nil}` were both admitted, because the governor
  guarded its arithmetic with `(number? load)` and its equality with
  `voltage-class` — so the two invariants the README calls
  \"arithmetic and equality, not field negotiation\" were enforced only
  on proposals that volunteered the numbers. Omitting them was a way to
  be approved. Well-formedness is checked BEFORE the invariants so that
  a missing number is refused rather than skipped."
  (:require [kotoba.lang.text :as str]))

;; Voltage classes this actor recognises (IEC-style bands). A circuit or
;; a piece of equipment outside this set is not a mismatch — it is a
;; value the actor cannot reason about at all, which is a different
;; refusal and carries a different fix.
(def voltage-classes #{:elv :lv :mv :hv :ehv})

(def supported
  "op -> {:summary :requires :escalates?}. `:requires` are the proposal
  fields without which the governor cannot perform its checks."
  {:approve-load
   {:summary    "Approve a proposed load against a registered circuit's rating."
    :requires   #{:circuit-id :load :voltage-class}
    :escalates? false}

   :energize-circuit
   {:summary    "Energize a registered circuit. Live work — always human sign-off."
    :requires   #{:circuit-id}
    :escalates? true}})

(def reserved
  "op -> why it is not this actor's to propose. These are refused with an
  authority reason, never admitted and never merely escalated: an
  escalation asks a human to approve THIS actor's proposal, and these
  are acts the actor may not put in front of a human as its own."
  {:disable-rcd-protection
   "残留電流保護の解除は保護装置の無効化であり、提案の対象外（有資格者の現場判断）"
   :bypass-protection-device
   "保護装置のバイパスは提案の対象外（有資格者の現場判断）"
   :work-live
   "活線作業は物理実行であり、この actor の範囲外（README の robotics-gated 前提）"
   :modify-earthing
   "接地系統の変更は物理実行であり、この actor の範囲外"
   :override-interlock
   "インターロックの無効化は提案の対象外"})

(defn classify
  "Classify `op` against the vocabulary. Total — every value of `op`,
  including nil and non-keywords, lands in exactly one bucket."
  [op]
  (cond
    (contains? supported op) {:status :supported :spec (get supported op)}
    (contains? reserved op)  {:status :reserved  :reason (get reserved op)}
    :else                    {:status :unsupported}))

(defn escalates?
  "Does this op always require human sign-off, independent of confidence?"
  [op]
  (boolean (get-in supported [op :escalates?])))

;; ---------------------------------------------------------------- shape

(defn- positive-number? [x] (and (number? x) (pos? x)))

(defn- field-fault
  "Why is `v` unusable as `field`? nil when it is usable. Kept here and
  not in the governor so that the vocabulary owns the shape of its own
  arguments."
  [field v]
  (case field
    :circuit-id    (when-not (and (string? v) (seq (str/trim v)))
                     "circuit-id は非空の文字列でなければならない")
    :load          (cond
                     (not (number? v)) "load は数値でなければならない（文字列の \"80\" は電流ではない）"
                     (not (pos? v))    "load は正の電流でなければならない")
    :voltage-class (cond
                     (not (keyword? v))              "voltage-class は keyword でなければならない"
                     (not (voltage-classes v))       (str "未知の電圧クラス " v
                                                          "（既知: " (pr-str (sort voltage-classes)) "）"))
    nil))

(defn malformed
  "Well-formedness violations of `proposal` for a SUPPORTED op. Returns
  a vector of {:rule :detail}, empty when well formed. Callers must have
  established that the op is supported — an unsupported op has no
  `:requires` and asking this of it would return `[]`, which reads as
  `well formed`."
  [proposal]
  (let [{:keys [requires]} (get supported (:op proposal))]
    (vec
     (concat
      ;; a required field that is absent entirely
      (for [f (sort requires)
            :when (nil? (get proposal f))]
        {:rule :incomplete-proposal
         :detail (str (name f) " が未指定（" (name (:op proposal))
                      " は " (pr-str (sort requires)) " を要する）")})
      ;; a required field that is present but unusable
      (for [f (sort requires)
            :let [v (get proposal f)]
            :when (some? v)
            :let [fault (field-fault f v)]
            :when fault]
        {:rule :ill-formed-field :detail fault})
      ;; confidence is not in :requires — it is common to every op
      (when-let [c (:confidence proposal)]
        (when-not (and (number? c) (<= 0 c 1))
          [{:rule :ill-formed-field
            :detail (str "confidence は 0..1 の数値でなければならない（受領: " (pr-str c) "）")}]))))))

(defn ratable-fault
  "Why can a REGISTERED circuit not be checked against? nil when it can.

  Measured on f509ac8: a circuit registered without `:ampacity` made
  `governor/check` throw NullPointerException, and one with a string
  ampacity threw ClassCastException. A crash is not a refusal — it
  produces no verdict, no violation and no ledger entry, so the one
  thing the actor exists to do does not happen. An unusable registry
  entry has to come back as a refusal the operator can read."
  [c]
  (cond
    (not (positive-number? (:ampacity c)))
    (str "circuit " (:circuit-id c) " に使用可能な :ampacity が無い（受領: "
         (pr-str (:ampacity c)) "）— 定格不明の回路に対して電流余裕は計算できない")

    (not (voltage-classes (:voltage-class c)))
    (str "circuit " (:circuit-id c) " に既知の :voltage-class が無い（受領: "
         (pr-str (:voltage-class c)) "）— 登録クラス不明の回路に機器の一致は判定できない")))
