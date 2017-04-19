(ns milo.server.http
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.set :as set]
            [cognitect.transit :as transit]
            [taoensso.timbre :as log]))

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

(defn missing-header
  [request header]
  (when (str/blank? (get-in request [:headers header]))
    {:status 400
     :headers {"Missing-Required-Header" header}}))

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

(defmulti response-body
  (fn
    [request body]
    (get-in request [:headers "accept"])))

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

(defn body-response
  [status request body]
  {:status status
   :headers {"Content-Type" (get-in request [:headers "accept"])}
   :body (response-body request body)})

(defn non-websocket-response
  []
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})
