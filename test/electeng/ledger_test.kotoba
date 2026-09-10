(ns electeng.ledger-test
  "The audit trail's one job: showing what authorised a write.

  Measured on f509ac8, an automatic commit and a commit reached through
  `actor/approve!` produced entries with identical keys
  `(:disposition :record)`. The README says :energize-circuit ALWAYS
  requires human sign-off; the ledger could not show that it had been
  given. These tests fail if that distinction is ever collapsed again."
  (:require [clojure.test :refer [deftest is testing]]
            [electeng.actor :as actor]
            [electeng.ledger :as ledger]
            [electeng.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-circuit! st {:circuit-id "C-1" :client-id "client-1"
                                 :name "panel-3 feeder"
                                 :ampacity 100 :voltage-class :lv})
    st))

;; ------------------------------------------------------- the value itself

(deftest an-entry-must-name-its-authorisation
  (testing "an entry that does not say what authorised it is refused at construction"
    (is (thrown? clojure.lang.ExceptionInfo
                 (ledger/entry {:disposition :commit :record {:x 1}})))
    (is (thrown? clojure.lang.ExceptionInfo
                 (ledger/entry {:disposition :commit :authorisation :because-i-said-so
                                :record {:x 1}})))))

(deftest a-commit-cannot-be-authorised-by-a-hold
  (is (thrown? clojure.lang.ExceptionInfo
               (ledger/entry {:disposition :commit :authorisation :governor-hold
                              :record {:x 1}}))))

(deftest a-hold-cannot-claim-sign-off
  (testing "a refusal is not something a human approved"
    (is (thrown? clojure.lang.ExceptionInfo
                 (ledger/entry {:disposition :hold :authorisation :human-sign-off
                                :verdict {:ok? false}})))))

(deftest a-commit-must-carry-its-record
  (is (thrown? clojure.lang.ExceptionInfo
               (ledger/entry {:disposition :commit :authorisation :governor-clear}))))

(deftest a-hold-must-carry-its-reason
  (is (thrown? clojure.lang.ExceptionInfo
               (ledger/entry {:disposition :hold :authorisation :governor-hold}))))

(deftest an-unknown-disposition-is-refused
  (is (thrown? clojure.lang.ExceptionInfo
               (ledger/entry {:disposition :maybe :authorisation :governor-clear
                              :record {:x 1}}))))

(deftest well-formed-entries-are-built
  (testing "the constructor must be capable of NOT throwing"
    (is (= {:disposition :commit :authorisation :governor-clear :record {:x 1}}
           (ledger/entry {:disposition :commit :authorisation :governor-clear
                          :record {:x 1}})))
    (is (= :human-sign-off
           (:authorisation (ledger/entry {:disposition :commit
                                          :authorisation :human-sign-off
                                          :record {:x 1}}))))))

(deftest authorisation-of-a-pre-existing-entry-is-nil-not-cleared
  (testing "entries written before this namespace get an honest nil, not :governor-clear"
    (is (nil? (ledger/authorisation-of {:disposition :commit :record {:x 1}})))
    (is (false? (ledger/human-signed? {:disposition :commit :record {:x 1}})))))

;; ------------------------------------------------- through the real actor

(deftest an-auto-commit-and-a-signed-commit-are-distinguishable
  (testing "measured byte-identical on f509ac8"
    (let [auto-st (fresh-store)
          auto-g  (actor/build-graph {:store auto-st})
          _ (actor/run-request! auto-g {:client-id "client-1" :op :approve-load
                                        :circuit-id "C-1" :load 80 :voltage-class :lv}
                                {} "t-auto")
          auto-entry (first (store/ledger auto-st))

          human-st (fresh-store)
          human-g  (actor/build-graph {:store human-st})
          _ (actor/run-request! human-g {:client-id "client-1" :op :energize-circuit
                                         :circuit-id "C-1" :stake :high}
                                {} "t-human")
          _ (actor/approve! human-g "t-human")
          human-entry (first (store/ledger human-st))]
      (is (= :governor-clear (ledger/authorisation-of auto-entry)))
      (is (= :human-sign-off (ledger/authorisation-of human-entry)))
      (is (false? (ledger/human-signed? auto-entry)))
      (is (true?  (ledger/human-signed? human-entry)))
      (is (not= (ledger/authorisation-of auto-entry)
                (ledger/authorisation-of human-entry))
          "the two paths must not write the same entry"))))

(deftest energization-is-never-recorded-as-governor-cleared
  (testing "the op the README says ALWAYS needs sign-off must never claim otherwise"
    (let [st (fresh-store)
          g  (actor/build-graph {:store st})]
      (actor/run-request! g {:client-id "client-1" :op :energize-circuit
                             :circuit-id "C-1" :stake :high} {} "t-e")
      (is (empty? (store/ledger st)) "nothing is written while interrupted")
      (actor/approve! g "t-e")
      (is (= [:human-sign-off] (mapv ledger/authorisation-of (store/ledger st)))))))

(deftest a-hold-is-recorded-with-the-verdict-that-caused-it
  (let [st (fresh-store)
        g  (actor/build-graph {:store st})]
    (actor/run-request! g {:client-id "client-1" :op :approve-load
                           :circuit-id "C-1" :load 150 :voltage-class :lv} {} "t-hold")
    (let [e (first (store/ledger st))]
      (is (= :hold (:disposition e)))
      (is (= :governor-hold (ledger/authorisation-of e)))
      (is (contains? (set (map :rule (get-in e [:verdict :violations])))
                     :ampacity-exceeded)
          "the hold records WHICH invariant refused it, not merely that one did"))))

(deftest a-reserved-op-commits-nothing-and-is-held-by-name
  (testing "measured on f509ac8: :disable-rcd-protection COMMITTED a record"
    (let [st (fresh-store)
          g  (actor/build-graph {:store st})]
      (actor/run-request! g {:client-id "client-1" :op :disable-rcd-protection
                             :circuit-id "C-1" :load 80 :voltage-class :lv} {} "t-r")
      (is (empty? (store/records-of st "client-1")))
      (let [e (first (store/ledger st))]
        (is (= :hold (:disposition e)))
        (is (contains? (set (map :rule (get-in e [:verdict :violations]))) :reserved-op))))))
