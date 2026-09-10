(ns electeng.operation-test
  "The vocabulary refusals, asserted by the NAME of the rule that fires.

  Each of these was measured as admitted on f509ac8 (see the docstrings
  in electeng.operation). A test here that only asserted `(:hard? v)`
  would pass for any refusal at all — including one fired by an
  unrelated invariant — so every case pins the rule literal it claims
  to be about. If someone renames :reserved-op to :unsupported-op,
  these fail, and that failing is the point."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set :as set]
            [electeng.operation :as operation]
            [electeng.store :as store]
            [electeng.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-circuit! st {:circuit-id "C-1" :client-id "client-1"
                                 :name "panel-3 feeder"
                                 :ampacity 100 :voltage-class :lv})
    st))

(def ^:private req {:client-id "client-1"})

(def ^:private base-proposal
  {:op :approve-load :effect :propose :circuit-id "C-1"
   :load 80 :voltage-class :lv :confidence 0.9 :stake :low})

(defn- approve [& kvs]
  (if (seq kvs) (apply assoc base-proposal kvs) base-proposal))

(defn- rules [proposal]
  (set (map :rule (:violations (governor/check req {} proposal (fresh-store))))))

;; ------------------------------------------------------------ vocabulary

(deftest classify-is-total
  (testing "every op value lands in exactly one bucket, nil included"
    (is (= :supported   (:status (operation/classify :approve-load))))
    (is (= :supported   (:status (operation/classify :energize-circuit))))
    (is (= :reserved    (:status (operation/classify :disable-rcd-protection))))
    (is (= :unsupported (:status (operation/classify :demolish-substation))))
    (is (= :unsupported (:status (operation/classify nil))))
    (is (= :unsupported (:status (operation/classify "approve-load"))))))

(deftest supported-and-reserved-do-not-overlap
  (testing "an op cannot be both this actor's to propose and not its to propose"
    (is (empty? (set/intersection (set (keys operation/supported))
                                          (set (keys operation/reserved)))))))

(deftest reserved-op-is-refused-as-authority-not-as-typo
  (testing "measured admitted on f509ac8: {:op :disable-rcd-protection} -> :ok? true"
    (is (contains? (rules (approve :op :disable-rcd-protection)) :reserved-op))
    (is (not (contains? (rules (approve :op :disable-rcd-protection)) :unsupported-op))
        "a reserved op sends the operator to the licensed electrician, not to a spellchecker")))

(deftest unsupported-op-is-refused-as-vocabulary-not-as-authority
  (is (contains? (rules (approve :op :demolish-substation)) :unsupported-op))
  (is (not (contains? (rules (approve :op :demolish-substation)) :reserved-op))))

(deftest every-reserved-op-is-actually-refused
  (testing "the map is not decoration — each entry produces the refusal it declares"
    (doseq [op (keys operation/reserved)]
      (is (contains? (rules (approve :op op)) :reserved-op)
          (str op " is listed as reserved but was not refused as such")))))

(deftest a-string-op-does-not-read-as-its-keyword
  (testing "\"approve-load\" looks supported to a human and bypassed both HARD checks"
    (is (contains? (rules (approve :op "approve-load")) :unsupported-op))))

(deftest nil-op-is-refused
  (is (contains? (rules (approve :op nil)) :unsupported-op)))

(deftest escalation-comes-from-the-vocabulary
  (is (true?  (operation/escalates? :energize-circuit)))
  (is (false? (operation/escalates? :approve-load)))
  (is (false? (operation/escalates? :demolish-substation))
      "an unknown op must not be escalated — it is refused, not put to a human"))

;; ------------------------------------------------------- well-formedness

(deftest an-approval-must-state-its-load
  (testing "measured admitted on f509ac8: {:load nil} -> :ok? true"
    (is (contains? (rules (approve :load nil)) :incomplete-proposal))))

(deftest an-approval-must-state-its-voltage-class
  (testing "measured admitted on f509ac8: {:voltage-class nil} -> :ok? true"
    (is (contains? (rules (approve :voltage-class nil)) :incomplete-proposal))))

(deftest a-string-load-is-not-a-current
  (testing "measured admitted on f509ac8: {:load \"80\"} -> :ok? true"
    (is (contains? (rules (approve :load "80")) :ill-formed-field))))

(deftest a-non-positive-load-is-refused
  (testing "measured admitted on f509ac8: {:load -50} and {:load 0} -> :ok? true"
    (is (contains? (rules (approve :load -50)) :ill-formed-field))
    (is (contains? (rules (approve :load 0)) :ill-formed-field))))

(deftest an-unknown-voltage-class-is-not-a-mismatch
  (testing "a class outside the known set cannot be compared at all"
    (is (contains? (rules (approve :voltage-class :gigavolt)) :ill-formed-field))
    (is (not (contains? (rules (approve :voltage-class :gigavolt)) :voltage-class-mismatch)))))

(deftest confidence-outside-0-1-is-refused
  (testing "measured admitted on f509ac8: {:confidence 2.5} -> :ok? true, above any floor"
    (is (contains? (rules (approve :confidence 2.5)) :ill-formed-field)))
  (testing "measured THROWN on f509ac8: {:confidence \"0.9\"} -> ClassCastException"
    (is (contains? (rules (approve :confidence "0.9")) :ill-formed-field))))

(deftest well-formed-proposals-raise-no-shape-violations
  (testing "the checks must be capable of NOT firing, or they discriminate nothing"
    (is (empty? (operation/malformed (approve))))
    (is (empty? (operation/malformed {:op :energize-circuit :circuit-id "C-1"
                                      :effect :propose :confidence 0.9})))
    (is (empty? (rules (approve))))))

(deftest energize-does-not-require-a-load
  (testing ":requires is per-op — energization states no load and stays well formed"
    (is (empty? (operation/malformed {:op :energize-circuit :circuit-id "C-1"
                                      :effect :propose})))))

;; -------------------------------------------------- unusable registry

(deftest an-unratable-circuit-is-refused-not-thrown
  (testing "measured on f509ac8: NullPointerException from inside governor/check"
    (let [st (store/mem-store)]
      (store/register-client! st {:client-id "client-1" :name "Kobo"})
      (store/register-circuit! st {:circuit-id "C-1" :client-id "client-1"})
      (let [v (governor/check req {} (approve) st)]
        (is (contains? (set (map :rule (:violations v))) :circuit-not-ratable))
        (is (true? (:hard? v)))))))

(deftest a-string-ampacity-is-refused-not-thrown
  (testing "measured on f509ac8: ClassCastException from inside governor/check"
    (let [st (store/mem-store)]
      (store/register-client! st {:client-id "client-1" :name "Kobo"})
      (store/register-circuit! st {:circuit-id "C-1" :client-id "client-1"
                                   :ampacity "100" :voltage-class :lv})
      (is (contains? (set (map :rule (:violations (governor/check req {} (approve) st))))
                     :circuit-not-ratable)))))

(deftest ratable-fault-is-nil-for-a-usable-circuit
  (is (nil? (operation/ratable-fault {:circuit-id "C-1" :ampacity 100 :voltage-class :lv}))))
