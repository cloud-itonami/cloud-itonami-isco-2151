(ns electeng.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [electeng.actor :as actor]
            [electeng.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-circuit! st {:circuit-id "C-1" :client-id "client-1"
                                 :name "panel-3 feeder"
                                 :ampacity 100 :voltage-class :lv})
    st))

(deftest commits-an-in-margin-load
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-load :stake :low
                 :circuit-id "C-1" :load 80 :voltage-class :lv}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-ampacity-load
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-load :stake :low
                 :circuit-id "C-1" :load 150 :voltage-class :lv}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-energizes-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :energize-circuit :stake :high
                 :circuit-id "C-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
