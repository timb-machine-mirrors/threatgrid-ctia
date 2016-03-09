(ns cia.properties
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import java.util.Properties
           java.io.BufferedReader
           java.io.File))

(def property-files ["cia.properties"
                     "cia-default.properties"])

(defonce properties (atom {}))

(defn load [^BufferedReader reader]
  (doto (Properties.)
    (.load reader)))

(defn transform [properties]
  (reduce (fn [accum [k v]]
            (let [parts (str/split k #"\.")]
              (cond
                (empty? parts) accum
                (= 1 (count parts)) (assoc accum (keyword k) v)
                :else (assoc-in accum (map keyword parts) v))))
          {}
          properties))

(defn set-properties! [file]
  (->> (io/reader file)
       load
       transform
       (reset! properties)))

(defn init!
  ([]
   (some->> (map #(-> % io/resource io/file) property-files)
            (filter some?)
            (filter #(.canRead ^File %))
            first
            set-properties!))
  ([file]
   (-> file
       io/resource
       set-properties!)))