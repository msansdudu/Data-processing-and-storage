(ns c6)

(def transaction-stats (atom 0))

(def empty-map
  {:forward {},
   :backward {}})

(defn route
  [route-map from to price tickets-num]
  (let [tickets (ref tickets-num :validator (fn [state] (>= state 0)))
        orig-source-desc (or (get-in route-map [:forward from]) {})
        orig-reverse-dest-desc (or (get-in route-map [:backward to]) {})
        route-desc {:price price,
                    :tickets tickets}
        source-desc (assoc orig-source-desc to route-desc)
        reverse-dest-desc (assoc orig-reverse-dest-desc from route-desc)]
    (-> route-map
        (assoc-in [:forward from] source-desc)
        (assoc-in [:backward to] reverse-dest-desc))))

(defn find-best-route [route-map from to]
  (loop [queue (sorted-set [0 from []])
         visited #{}]
    (if (empty? queue)
      nil
      (let [[cost current path] (first queue)
            rest-queue (disj queue [cost current path])
            new-path (conj path current)]
        (if (= current to)
          {:price cost :path new-path}
          (if (contains? visited current)
            (recur rest-queue visited)
            (let [neighbors (get-in route-map [:forward current])
                  next-steps (for [[next-city desc] neighbors
                                   :let [ticket-ref (:tickets desc)
                                         ticket-count @ticket-ref
                                         price (:price desc)]
                                   :when (> ticket-count 0)]
                               [(+ cost price) next-city new-path])]
              (recur (into rest-queue next-steps)
                     (conj visited current)))))))))

(defn book-tickets
  [route-map from to]
  (dosync
   (swap! transaction-stats inc)
   
   (if (= from to)
     {:path '(), :price 0}
     (let [result (find-best-route route-map from to)]
       (if result
         (let [path (:path result)
               segments (partition 2 1 path)]
           (doseq [[seg-from seg-to] segments]
             (let [ticket-ref (get-in route-map [:forward seg-from seg-to :tickets])]
               (alter ticket-ref dec)))
           result)
         {:error "No tickets or route not found"})))))

(def spec1 (-> empty-map
               (route "City1" "Capital" 200 5)
               (route "Capital" "City1" 250 5)
               (route "City2" "Capital" 200 5)
               (route "Capital" "City2" 250 5)
               (route "City3" "Capital" 300 3)
               (route "Capital" "City3" 400 3)
               (route "City1" "Town1_X" 50 2)
               (route "Town1_X" "City1" 150 2)
               (route "Town1_X" "TownX_2" 50 2)
               (route "TownX_2" "Town1_X" 150 2)
               (route "Town1_X" "TownX_2" 50 2)
               (route "TownX_2" "City2" 50 3)
               (route "City2" "TownX_2" 150 3)
               (route "City2" "Town2_3" 50 2)
               (route "Town2_3" "City2" 150 2)
               (route "Town2_3" "City3" 50 3)
               (route "City3" "Town2_3" 150 2)))

(defn booking-future [route-map from to init-delay loop-delay]
  (future
    (Thread/sleep init-delay)
    (loop [bookings []]
      (Thread/sleep loop-delay)
      (let [booking (book-tickets route-map from to)]
        (if (:error booking)
          bookings
          (recur (conj bookings booking)))))))

(defn print-bookings [name ft]
  (println (str name ":") (count ft) "bookings")
  (doseq [booking ft]
    (println "  price:" (:price booking) "path:" (:path booking))))

(defn run []
  (reset! transaction-stats 0)
  
  (let [f1 (booking-future spec1 "City1" "City3" 0 10),
        f2 (booking-future spec1 "City1" "City2" 50 10),
        f3 (booking-future spec1 "City2" "City3" 100 10)]
    
    (print-bookings "City1->City3" @f1)
    (print-bookings "City1->City2" @f2)
    (print-bookings "City2->City3" @f3)
    
    (println "---")
    (println "Total transaction (re-)starts:" @transaction-stats)))

(defn -main [& _]
  (run)
  (shutdown-agents))