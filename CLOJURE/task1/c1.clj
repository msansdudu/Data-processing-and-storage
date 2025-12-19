(ns c1
  (:gen-class)
  (:require [clojure.string :as str]))

(defn strings-no-repeat [alphabet n]
  (let [alph (map str alphabet)]
    (reduce
     (fn [acc _]
       (mapcat
        (fn [s]
          (map (fn [c] (str s c))
               (remove #(= % (str (last s))) alph)))
        acc))
     alph
     (range 1 n))))

(defn -main [& args]
  (let [alphabet (str/split (first args) #",")
        n        (Integer/parseInt (second args))]
    (println (strings-no-repeat alphabet n))))