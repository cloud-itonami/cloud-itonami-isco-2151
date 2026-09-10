(ns electeng.store
  "SSoT for the ISCO-08 2151 community electrical engineers actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section).
  Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client  — a registered organization (:client-id, :name)
    circuit — a registered electrical circuit {:circuit-id :client-id
              :name :ampacity number :voltage-class kw}. `:ampacity`
              is the registered maximum current (A) a proposed load
              must not exceed; `:voltage-class` is the registered
              class (e.g. :lv, :mv, :hv) equipment on this circuit
              must match.
    record  — a committed operating record (approved load, approved
              energization) — written ONLY via commit-record!.
    ledger  — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (circuit [s circuit-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-circuit! [s c])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (circuit [_ circuit-id] (get-in @a [:circuits circuit-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-circuit! [s c]
    (swap! a assoc-in [:circuits (:circuit-id c)] c) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :circuits {} :records [] :ledger []}
                                   seed)))))
