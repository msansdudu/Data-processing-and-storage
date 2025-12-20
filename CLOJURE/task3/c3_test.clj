(ns c3-test
  (:require [clojure.test :refer :all]
            [c3 :refer :all]))

(deftest finite-test
  (is (= (filter even? (range 100))
         (pfilter even? (range 100) 10 4))))

(deftest infinite-test
  (is (= (take 20 (filter odd? (range)))
         (take 20 (pfilter odd? (range) 8 3)))))

(deftest empty-test
  (is (empty? (pfilter pos? [] 4 2))))
