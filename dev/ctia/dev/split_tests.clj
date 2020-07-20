(ns ctia.dev.split-tests
  (:require [circleci.test :as t]
            [clojure.edn :as edn]
            [clojure.test :as test]
            [clojure.pprint :as pprint]))

; Algorithm By Mark Dickinson https://stackoverflow.com/a/2660138
(defn partition-fairly
  "Partition coll into n chunks such that each chunk's
  count is within one of eachother. Puts its larger chunks first.
  Returns a vector of chunks (vectors)."
  [n coll]
  {:pre [(integer? n)]
   :post [(or (empty? %)
              (let [fc (count (first %))]
                (every? #{fc (dec fc)} (map count %))))]}
  ;TODO make lazier (use partition with overlapping steps to iterate
  ; over `indices`)
  (let [coll (vec coll)
        q (quot (count coll) n)
        r (rem (count coll) n)
        indices (mapv #(+ (* q %)
                          (min % r))
                      (range (inc n)))]
    (mapv #(subvec coll
                   (indices %)
                   (indices (inc %)))
          (range n))))

(defn read-env-config
  "Returns CTIA_SPLIT_TESTS as Clojure data."
  []
  {:post [((every-pred vector?
                       (comp #{2} count)
                       #(every? integer? %))
           %)
          (let [[this-split total-splits] %]
            (< -1 this-split total-splits))]}
  (or (some-> (System/getenv "CTIA_SPLIT_TESTS")
              edn/read-string)
      ; default: this is the first split of total 1 split. (ie., run everything)
      [0 1]))

(defn nses-for-this-build [[this-split total-splits] nses]
  (as-> nses nses
    ;stabilize order across builds
    (sort nses)
    ;calculate all splits
    (partition-fairly total-splits nses)
    ;select this split
    (nth nses this-split)))

;Derived from https://github.com/circleci/circleci.test/blob/master/src/circleci/test.clj
;
;Copyright © 2017-2020 Circle Internet Services and contributors
;
;Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
;
; See [[LICENSE]] for a copy of EPL 1.0.
(defn dir
  "Same usage as circleci.test/dir. To split tests, bind
  environment variable CTIA_SPLIT_TESTS to a string containing a vector
  pair [n m], where m is the number of splits, and n identifies the
  current split in `(range m)`.
  
  eg., CTIA_SPLIT_TESTS=\"[0 2]\" lein test       ; run the first half of the tests
  eg., CTIA_SPLIT_TESTS=\"[1 2]\" lein test       ; run the second half of the tests
  "
  ([dirs-str] (dir dirs-str ":default"))
  ([dirs-str selector-str]
   (let [;; This function is designed to be used with Leiningen aliases only, since
         ;; adding :project/test-dirs to an alias will pass in data from the project
         ;; map as an argument; however it passes it in as a string.
         _ (when-not (try (coll? (read-string dirs-str)) (catch Exception _))
             (binding [*out* *err*]
               (println "Please see the readme for usage of this function.")
               (System/exit 1)))
         [this-split total-splits :as split-config] (read-env-config)
         all-nses (vec (@#'t/nses-in-directories (read-string dirs-str)))
         nses (vec (nses-for-this-build split-config all-nses))
         _ (apply require :reload nses)
         selector (@#'t/lookup-selector (t/read-config!) (read-string selector-str))
         _ (if (#{[0 1]} split-config)
             (println "[ctia.dev.split-tests] Running all tests")
             (println
               (str "[ctia.dev.split-tests] Splitting tests. "
                    "This is chunk " (inc this-split) " of " total-splits " testing "
                    (count nses) " of " (count all-nses) " test namespaces: "
                    nses)))
         summary (@#'t/run-selected-tests selector nses)]
     (System/exit (+ (:error summary) (:fail summary))))))
