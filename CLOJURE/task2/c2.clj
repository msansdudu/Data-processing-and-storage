(ns c2)

(defn sieve
  []
  (letfn [(next-primes [candidate table]
            (lazy-seq
             (if-let [ps (get table candidate)]
               (let [updated (reduce (fn [t p]
                                       (update t (+ candidate p) (fnil conj []) p))
                                     (dissoc table candidate)
                                     ps)]
                 (next-primes (inc candidate) updated))
               (cons candidate
                     (next-primes (inc candidate)
                                  (assoc table (* candidate candidate) [candidate]))))))]
    (next-primes 2 {})))

(defn -main [& args]
  (let [n (try (Integer/parseInt (first args)) (catch Exception _ 10))
        xs (take n (sieve))]
    (println (vec xs))))