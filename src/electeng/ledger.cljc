(ns electeng.ledger
  "Audit entries for the ISCO-08 2151 electrical engineers actor.

  Measured on f509ac8, before this namespace existed. Two runs, one an
  automatic `:approve-load` and one an `:energize-circuit` that
  interrupted and was resumed through `actor/approve!`, produced
  entries of the same shape:

    {:disposition :commit :record {...}}
    {:disposition :commit :record {...}}

  Keys `(:disposition :record)` both times. The README says
  `:energize-circuit` ALWAYS requires human sign-off — and the ledger
  could not show that any given write had received it. The distinction
  survived only in the op name, which is the payload, not the
  authorisation. An audit trail whose entries do not record what
  authorised the write cannot answer the one question it is kept for.

  So an entry names its `:authorisation` and this namespace refuses to
  build one without it:

    :governor-clear  the governor returned :ok? true; no human involved.
    :human-sign-off  the run interrupted at :request-approval and a
                     human resumed the thread. The act of resuming IS
                     the approval, so it is recorded as one.
    :governor-hold   the governor refused; nothing was committed.

  `entry` is total and pure: it either returns a well-formed entry or
  throws, and it never reaches a store. Building an entry is not
  appending one.")

(def authorisations #{:governor-clear :human-sign-off :governor-hold})

(defn- fault [{:keys [disposition authorisation record verdict]}]
  (cond
    (not (#{:commit :hold} disposition))
    (str ":disposition は :commit か :hold（受領: " (pr-str disposition) "）")

    (not (authorisations authorisation))
    (str ":authorisation が無い、または未知（受領: " (pr-str authorisation)
         "、既知: " (pr-str (sort authorisations)) "）"
         " — 台帳の項目は、その書き込みを何が許可したかを名乗らなければならない")

    (and (= :commit disposition) (= :governor-hold authorisation))
    ":commit を :governor-hold が許可することはない"

    (and (= :hold disposition) (not= :governor-hold authorisation))
    (str ":hold の :authorisation は :governor-hold のみ（受領: " (pr-str authorisation) "）")

    (and (= :commit disposition) (nil? record))
    ":commit には :record が要る"

    (and (= :hold disposition) (nil? verdict))
    ":hold には拒否理由としての :verdict が要る"))

(defn entry
  "Build one audit entry. Throws on anything ill-formed — a ledger that
  accepts an entry it cannot interpret is worse than one that refuses,
  because the refusal is visible and the bad entry is not."
  [{:keys [disposition authorisation record verdict] :as m}]
  (when-let [f (fault m)]
    (throw (ex-info (str "ill-formed ledger entry: " f) {:entry m :fault f})))
  (cond-> {:disposition   disposition
           :authorisation authorisation}
    record (assoc :record record)
    verdict (assoc :verdict (select-keys verdict [:ok? :hard? :escalate? :confidence :violations]))))

(defn human-signed?
  "Did a human sign this entry off? The question the ledger exists to
  answer, asked of one entry."
  [e]
  (= :human-sign-off (:authorisation e)))

(defn authorisation-of
  "What authorised this write? nil for entries written before this
  namespace existed — which is the honest answer for them, and is
  deliberately not conflated with :governor-clear."
  [e]
  (:authorisation e))
