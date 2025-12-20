(ns c5
  (:require [clojure.string :as str]))

(defn make-fork [id]
  (ref {:id id
        :busy? false
        :count 0}))

(defn choose-order [mode i f1 f2]
  (case mode
    :fixed   [f1 f2]
    :reverse [f2 f1]
    :parity  (if (even? i) [f1 f2] [f2 f1])
    :random  (if (zero? (rand-int 2)) [f1 f2] [f2 f1])
    ;; default: global ordering
    (if (< (System/identityHashCode f1)
           (System/identityHashCode f2))
      [f1 f2]
      [f2 f1])))

(defn take-forks!
  [order idx left right retry-counter]
  (loop []
    (let [res
          (dosync
           (let [[a b] (choose-order order idx left right)]
             (if (or (:busy? @a) (:busy? @b))
               :fail
               (do
                 (alter a assoc :busy? true)
                 (alter b assoc :busy? true)
                 :ok))))]
      (if (= res :ok)
        :done
        (do
          (swap! retry-counter inc)
          (Thread/yield)
          (recur))))))

(defn drop-forks!
  [f1 f2]
  (dosync
   (doseq [f [f1 f2]]
     (alter f (fn [s]
                (-> s
                    (assoc :busy? false)
                    (update :count inc)))))))

(defn philosopher
  [{:keys [id forks rounds think eat order retries]}]
  (let [n (count forks)
        left  (forks id)
        right (forks (mod (inc id) n))]
    (loop [r rounds eaten 0]
      (if (zero? r)
        {:id id :meals eaten}
        (do
          (when (pos? think) (Thread/sleep think))
          (take-forks! order id left right retries)
          (when (pos? eat) (Thread/sleep eat))
          (drop-forks! left right)
          (recur (dec r) (inc eaten)))))))

(defn run-sim
  [{:keys [n rounds think eat order timeout]
    :or {n 5 rounds 10 think 5 eat 5 order :parity}}]
  (let [forks   (vec (map make-fork (range n)))
        retries (atom 0)
        start   (promise)
        tasks   (mapv
                 (fn [i]
                   (future
                     @start
                     (philosopher {:id i
                                   :forks forks
                                   :rounds rounds
                                   :think think
                                   :eat eat
                                   :order order
                                   :retries retries})))
                 (range n))
        t0 (System/nanoTime)]

    (deliver start true)

    (let [deadline (when timeout (+ (System/currentTimeMillis) timeout))
          results
          (loop [fs tasks acc []]
            (if (empty? fs)
              acc
              (let [f (first fs)
                    wait (when deadline (max 1 (- deadline (System/currentTimeMillis))))
                    v (if timeout
                        (deref f wait ::timeout)
                        (deref f))]
                (if (= v ::timeout)
                  ::timeout
                  (recur (rest fs) (conj acc v))))))

          elapsed (/ (- (System/nanoTime) t0) 1e6)
          usage   (mapv #(-> @% :count) forks)]

      (if (= results ::timeout)
        {:status :timeout
         :time elapsed
         :retries @retries
         :forks usage}
        {:status :ok
         :time elapsed
         :retries @retries
         :forks usage
         :philosophers results}))))

(defn report [m]
  (str/join
   \newline
   [(str "Status: " (:status m))
    (format "Time: %.2f ms" (:time m))
    (str "Transaction restarts: " (:retries m))
    (str "Fork usage: " (:forks m))
    (when-let [p (:philosophers m)]
      (str "Meals per philosopher: " (mapv :meals p)))]))

(defn parse-args [args]
  (loop [args args
         opts {}]
    (if (empty? args)
      opts
      (let [[k v & rest] args
            key (keyword (subs k 2))
            val (try
                  (Integer/parseInt v)
                  (catch Exception _ v))]
        (recur rest (assoc opts key val))))))

(defn -main [& args]
  (let [cli-opts (parse-args args)
        opts {:n       (get cli-opts :n 5)
              :rounds  (get cli-opts :rounds 20)
              :think   (get cli-opts :think 2)
              :eat     (get cli-opts :eat 2)
              :order   (keyword (get cli-opts :order "parity"))
              :timeout (get cli-opts :timeout nil)}
        res (run-sim opts)]
    (println (report res))
    (shutdown-agents)))