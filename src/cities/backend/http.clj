(ns cities.backend.http
  (:require [cognitect.transit :as transit]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.set :as set]
            [clojure.spec.alpha :as spec]
            [taoensso.timbre :as log]
            [clojure.string :as str]))

(def supported-media-types #{"application/edn"
                             "application/transit+json"
                             "application/transit+msgpack"})

(defn unsupported-media-type?
  [{headers :headers}]
  (if-let [content-type (get headers "content-type")]
    (not (contains? supported-media-types content-type))
    true))

(defn unsupported-media-type
  [request]
  (when (unsupported-media-type? request)
    {:status 415
     :headers {"Accepts" supported-media-types}}))

(defn not-acceptable?
  [{headers :headers}]
  (if-let [accept (get headers "accept")]
    (not (contains? supported-media-types accept))
    true))

(defn not-acceptable
  [request]
  (when (not-acceptable? request)
    {:status 406
     :headers {"Consumes" supported-media-types }}))

(defmulti parsed-body
  (fn [request]
    (get-in request [:headers "content-type"])))

(defmethod parsed-body "application/edn"
  [request]
  (try
    (-> request :body slurp edn/read-string)
    (catch Exception ex
      (log/error ex "Failed to parse edn request body."))))

(defmethod parsed-body "application/transit+json"
  [{body :body}]
  (try
    (transit/read (transit/reader body :json))
    (catch Exception ex
      (log/error ex "Failed to parse transit+json request body."))))

(defmethod parsed-body "application/transit+msgpack"
  [{body :body}]
  (try
    (transit/read (transit/reader body :msgpack))
    (catch Exception ex
      (log/error ex "Failed to parse transit+msgpack request body."))))

(defn resolve-accept [request]
  (-> request
      (get-in [:headers "accept"])
      (str/split #",")
      (first)))

(defmulti response-body
  (fn [request body]
    (resolve-accept request)))

(defmethod response-body "*/*"
  [request body]
  (pr-str body))

(defmethod response-body "application/edn"
  [request body]
  (pr-str body))

(defmethod response-body "application/transit+json"
  [request body]
  (try
    (let [out (java.io.ByteArrayOutputStream.)]
      (transit/write (transit/writer out :json) body)
      (.toByteArray out))
    (catch Exception ex
      (throw (ex-info "Failed to write transit+json response body."
                      {:request request
                       :body body
                       :exception ex})))))

(defmethod response-body "application/transit+msgpack"
  [request body]
  (try
    (let [out (java.io.ByteArrayOutputStream.)]
      (transit/write (transit/writer out :msgpack) body)
      (.toByteArray out))
    (catch Exception ex
      (throw (ex-info "Failed to write transit+msgpack response body."
                      {:request request
                       :body body
                       :exception ex})))))


(defmethod response-body "text/html"
  [request body]
  (try
    (str "<!DOCTYPE html><html><body><pre>"
         (with-out-str (pp/pprint body))
         "</pre></body></html>")
    (catch Exception ex
      (throw (ex-info "Failed to write text/html response body."
                      {:request request
                       :body body
                       :exception ex})))))

(defn body-response
  [status request body]
  {:status status
   :headers {"Content-Type" (resolve-accept request)}
   :body (response-body request body)})

(defmacro with-body
  [[body-sym body-spec request] & body]
  `(or (unsupported-media-type ~request)
       (not-acceptable ~request)
       (let [~body-sym (parsed-body ~request)]
         (if-not ~body-sym
           (body-response 400 ~request {:cities.backend/message "Invalid request body representation."})
           (if-let [validation-failure# (spec/explain-data ~body-spec ~body-sym)]
             (body-response 400 ~request {:cities.backend/message "Invalid request body."
                                          :cities.backend/data validation-failure#})
             ~@body)))))

(defmacro handle-exceptions
  [request & body]
  `(try
     ~@body
     (catch Exception e#
       (log/error e# "An exception was thrown while processing a request.")
       (body-response 500 ~request {:cities.backend/message "An error occurred."}))))
