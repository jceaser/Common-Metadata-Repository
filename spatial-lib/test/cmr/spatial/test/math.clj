(ns cmr.spatial.test.math
  (:refer-clojure :exclude [abs])
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.test.test-check-ext :as gen-ext :refer [defspec]]
   [cmr.common.util :as u]
   [cmr.spatial.math :refer  [PI abs acos antipodal-lon approx= asin atan atan2 cos degrees
                              double->float float->double float-type? radians range-intersects?
                              round sin sqrt tan within-range?]]
   [cmr.spatial.test.generators :as sgen]
   [primitive-math]))

(primitive-math/use-primitive-operators)

(defn math-values-close?
  "Helper for testing math value accuracy"
  [^double v1 ^double v2]
  (or (and (Double/isNaN v1)
           (Double/isNaN v2))
      (< (Math/abs (- v1 v2)) 0.000000000001)))

(def doubles-gen
  (gen/fmap double gen/ratio))

;; Tests the accuracy of the math functions provided by the Jafama library. It ensures they are up
;; to par with the Java Math.
(declare test-math-accuracy)
(defspec test-math-accuracy 2000
  (for-all [dvalue doubles-gen]
    (let [^double d dvalue]
      (and
        (math-values-close? (Math/cos d) (cos d))
        (math-values-close? (Math/sin d) (sin d))
        (math-values-close? (Math/acos d) (acos d))
        (math-values-close? (Math/asin d) (asin d))
        (math-values-close? (Math/tan d) (tan d))
        (math-values-close? (Math/atan d) (atan d))
        (math-values-close? (Math/abs d) (abs d))
        (math-values-close? (Math/sqrt d) (sqrt d))))))

(declare test-math-atan2-accuracy)
(defspec test-math-atan2-accuracy 2000
  (for-all [dvalue1 doubles-gen
            dvalue2 doubles-gen]
    (let [^double d1 dvalue1
          ^double d2 dvalue2]
      (math-values-close? (Math/atan2 d1 d2) (atan2 d1 d2)))))

(declare double-to-float-round-up-spec)
(defspec double-to-float-round-up-spec 2000
  (for-all [^double dvalue doubles-gen]
    (let [fvalue (double->float dvalue true)
          dvalue2 (float->double fvalue)]
      (and (float-type? fvalue)
           (>= dvalue2 dvalue)))))

(declare double-to-float-round-down-spec)
(defspec double-to-float-round-down-spec 2000
  (for-all [^double dvalue doubles-gen]
    (let [fvalue (double->float dvalue false)
          dvalue2 (float->double fvalue)]
      (and (float-type? fvalue)
           (<= dvalue2 dvalue)))))

(declare round-spec)
(defspec round-spec 2000
  (for-all [precision (gen/choose 0 10)
            n (gen-ext/choose-double -100 100)]
    (= (Double. (format (str "%." precision "f") n))
       (round precision n))))

(declare radians-degrees-spec)
(defspec radians-degrees-spec 100
  (for-all [d (gen-ext/choose-double -360 360)]
    (let [r (radians d)
          d2 (degrees r)]
      (and
        ;; Converting to radians then back to degrees should match
        (approx= d d2)

        ;; Radians should be within -2 PI and +2 PI
        (within-range? r (* -2.0 PI) (* 2.0 PI))))))

(deftest approx-equal-test
  (testing "changing the delta"
    (is (approx= 0.0001 0.0002 0.0001))
    (is (approx= 0.0001 0.0002 0.001))
    (is (not (approx= 0.0001 0.00021 0.0001)))
    (is (approx= 0.0001 0.00021 0.001)))
  (testing "vectors"
    (is (approx= [1] [1]))
    (is (approx= [1] [1.0]))
    (is (not (approx= [1] [1 2])))
    (is (not (approx= [1] []))))
  (testing "vector and lazy sequence"
    (let [items [1 2 3.0000001]]
      (is (approx= items (map identity items)))
      (is (approx= (map identity items) items))))
  (testing "vectors and lists"
    (is (approx= [1 2 3] '(1 2 3.0000001)))
    (is (not (approx= [1 2 3] '(1 3.0000001 2))) "order matters")
    (is (not (approx= [1 2 3] '(1 2 3.1)))))

  (testing "maps and sequences"
    (is (not (approx= {} [])))
    (is (not (approx= [] {})))))

(declare antipodal-lon-spec)
(defspec antipodal-lon-spec 100
  (for-all [_lon sgen/lons]
    (let [lon 76.85714285714286
          ^double opposite-lon (antipodal-lon lon)]
      (and (within-range? opposite-lon -180.0 180.0)
           (approx= (+ (abs opposite-lon) (abs lon)) 180.0)))))

(declare range-intersects-commutative-spec)
(defspec range-intersects-commutative-spec 1000
  (for-all [r1min doubles-gen
            r1size (gen/fmap #(abs ^double %) doubles-gen)
            r2min doubles-gen
            r2size (gen/fmap #(abs ^double %) doubles-gen)]
    (let [^double r1min r1min
          ^double r1size r1size
          ^double r2min r2min
          ^double r2size r2size
          r1max (+ r1min r1size)
          r2max (+ r2min r2size)]
      (is (= (range-intersects? r1min r1max r2min r2max)
         (range-intersects? r2min r2max r1min r1max))))))

(deftest range-intersects-test
  (testing "intersect cases"
    (u/are3 [r1min r1max r2min r2max]
            (is (true? (range-intersects? r1min r1max r2min r2max)))
            "Completely contained"
            1 10, 2 5
            "intersects beginning"
            1 10, 0 5
            "intersects end"
            1 10, 5 11
            "starts at end"
            1 10, 10 11
            "ends at beginning"
            1 10, 0 1
            "contains it"
            1 10, 0 11
            "identical"
            1 10, 1 10))
  (testing "does not intersect cases"
    (u/are3 [r1min r1max r2min r2max]
            (is (not (range-intersects? r1min r1max r2min r2max)))
            "before"
            1 10, -1 0
            "after"
            1 10, 11 12)))
