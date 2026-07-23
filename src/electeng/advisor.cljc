(ns electeng.advisor
  "ElectricalEngineersAdvisor — proposes a circuit-load operation
  (approve a load, energize a circuit) for a registered organization.
  Swappable mock/llm; the advisor ONLY proposes — `electeng.governor`
  checks the ampacity margin and voltage-class match independently.
  Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-load|:energize-circuit
               :effect :propose :circuit-id str :load number
               :voltage-class kw :stake kw :confidence n :rationale str}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake circuit-id load voltage-class] :as request}]
  {:op op
   :effect :propose
   :circuit-id circuit-id
   :load load
   :voltage-class voltage-class
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are an electrical engineering advisor. Given a request, propose
   an :op, the :circuit-id, :load and :voltage-class, an honest
   :confidence and a :stake. Never call an over-ampacity load or a
   voltage-class mismatch conforming — the governor checks both
   against the registered circuit rating.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
