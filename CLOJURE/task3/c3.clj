(ns c3)

(defn pfilter
  ([pred coll]
   (pfilter pred coll 64 4))

  ([pred coll block-size parallelism]
   (let [bs (max 1 (int block-size))
         ws (max 1 (int parallelism))
         blocks (partition-all bs coll)]

     (letfn [(spawn [blk]
               (future (doall (filter pred blk))))

             (consume [active rest-blocks]
               (lazy-seq
                (when (seq active)
                  (let [f        (first active)
                        tail     (subvec active 1)
                        next-fut (when-let [b (first rest-blocks)]
                                   (spawn b))
                        active'  (cond-> tail next-fut (conj next-fut))
                        result   @f]
                    (if (seq result)
                      (concat result
                              (consume active' (rest rest-blocks)))
                      (consume active' (rest rest-blocks)))))))]

       (consume
        (vec (map spawn (take ws blocks)))
        (drop ws blocks))))))


(defn -main [& args]
  (let [n   (try (Integer/parseInt (first args)) (catch Exception _ 100000))
        par (try (Integer/parseInt (second args)) (catch Exception _ 4))
        bs  (try (Integer/parseInt (nth args 2)) (catch Exception _ 1024))
        xs  (range n)
        pred #(zero? (mod % 3))]

    (let [t1 (System/nanoTime)
          a1 (count (filter pred xs))
          t2 (System/nanoTime)
          a2 (count (pfilter pred xs bs par))
          t3 (System/nanoTime)]

      (println "serial count:" a1 "time ms:" (/ (- t2 t1) 1e6))
      (println "pfilter count:" a2 "time ms:" (/ (- t3 t2) 1e6))
      (println "first 20 from infinite:"
               (take 20 (pfilter odd? (range))))))

  (shutdown-agents))