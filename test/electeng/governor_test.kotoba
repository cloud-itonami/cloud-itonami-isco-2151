(ns electeng.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [electeng.store :as store]
            [electeng.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-circuit! st {:circuit-id "C-1" :client-id "client-1"
                                 :name "panel-3 feeder"
                                 :ampacity 100 :voltage-class :lv})
    st))

(defn- approve [load vclass]
  {:op :approve-load :effect :propose :circuit-id "C-1"
   :load load :voltage-class vclass :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-ampacity-and-matching-class
  (let [st (fresh-store)
        v (governor/check req {} (approve 80 :lv) st)]
    (is (:ok? v))))

(deftest ok-at-exact-ampacity
  (testing "load exactly equal to the rating is within margin"
    (let [st (fresh-store)
          v (governor/check req {} (approve 100 :lv) st)]
      (is (:ok? v)))))

(deftest hard-on-ampacity-exceeded
  (testing "current arithmetic is not negotiable in the field"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (approve 120 :lv) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :ampacity-exceeded (:rule %)) (:violations v))))))

(deftest hard-on-voltage-class-mismatch
  (let [st (fresh-store)
        v (governor/check req {} (approve 80 :mv) st)]
    (is (:hard? v))
    (is (some #(= :voltage-class-mismatch (:rule %)) (:violations v)))))

(deftest hard-on-unknown-circuit
  (let [st (fresh-store)
        v (governor/check req {} (assoc (approve 80 :lv) :circuit-id "C-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-circuit (:rule %)) (:violations v)))))

(deftest hard-on-foreign-circuit
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (approve 80 :lv) st)]
      (is (:hard? v))
      (is (some #(= :circuit-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (approve 80 :lv) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (approve 80 :lv) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-energization
  (let [st (fresh-store)
        v (governor/check req {} {:op :energize-circuit :effect :propose
                                  :circuit-id "C-1" :confidence 0.9 :stake :high} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (approve 80 :lv) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
