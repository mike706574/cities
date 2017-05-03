(ns milo.client.misc
  (:require [cljs.pprint :as pprint]))

(defn pretty [x] (with-out-str (pprint/pprint x)))
