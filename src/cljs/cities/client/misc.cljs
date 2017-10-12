(ns cities.client.misc
  (:require [cljs.pprint :as pprint]
            [clojure.walk :as walk]))

(defn unlist
  [form]
  (walk/prewalk #(if (list? %) (vec %) %) form))

(defn pretty
  [form]
  (with-out-str (pprint/pprint form)))

(defn map-vals
  [f coll]
  (into {} (map (fn [[k v]] [k (f v)]) coll)))
