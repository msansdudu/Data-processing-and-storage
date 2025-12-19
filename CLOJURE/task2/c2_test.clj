(ns c2-test
  (:require [clojure.test :refer :all]
            [c2 :refer [sieve]]))

(deftest first-ten-primes
  (is (= [2 3 5 7 11 13 17 19 23 29]
         (vec (take 10 (sieve))))))

(deftest nth-prime
  (is (= 104729 (nth (sieve) 9999))))

(deftest no-even-primes-except-two
  (let [xs (take 1000 (sieve))]
    (is (= 2 (first xs)))
    (is (every? odd? (rest xs)))))