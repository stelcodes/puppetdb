(ns puppetlabs.puppetdb.testutils.http
  (:require [clojure.test :refer :all]
            [puppetlabs.puppetdb.http.server :as server]
            [puppetlabs.puppetdb.testutils :as tu]
            [puppetlabs.puppetdb.testutils.db :refer [*db* *read-db* with-test-db]]
            [puppetlabs.puppetdb.http :as http]
            [puppetlabs.puppetdb.time :as t]
            [puppetlabs.puppetdb.middleware
             :refer [wrap-with-puppetdb-middleware]]
            [puppetlabs.puppetdb.cheshire :as json])
  (:import
   (java.io ByteArrayInputStream)
   (java.net HttpURLConnection)))

(defmacro are-error-response-headers [headers]
  ;; A macro so the "is" line numbers will be right
  `(let [headers# ~headers]
     (is (= ["Content-Type"] (keys headers#)))
     (is (and (headers# "Content-Type")
              (http/error-ctype? (headers# "Content-Type"))))))

(defn vector-param
  [method order-by]
  (if (= :get method)
    (json/generate-string order-by)
    order-by))

(def ^:dynamic *app* nil)

(defn query-response
  ([method endpoint]      (query-response method endpoint nil))
  ([method endpoint query] (query-response method endpoint query {}))
  ([method endpoint query params]
   (*app* (tu/query-request method endpoint query {:params params}))))

(defmacro is-query-result
  [method endpoint query expected-results]
  `(let [response# (query-response ~method ~endpoint ~query)
         status# (:status response#)
         actual-result# (tu/parse-result (:body response#))
         expected-results# ~expected-results]
     (is (= (count expected-results#) (count actual-result#)))
     (is (coll? actual-result#))
     (is (= expected-results# (set actual-result#)))
     (is (= HttpURLConnection/HTTP_OK status#))))

(defn slurp-unless-string
  [response-body]
  (if (string? response-body)
    response-body
    (slurp response-body)))

(defn convert-response
  [response]
  (let [body-string
        (-> response
            :body
            slurp-unless-string)]
    (try
      (vec (json/parse-string body-string true))
      (catch Exception e
        (println "Error parsing repsonse string as json. Response string is:\n    " body-string)
        (throw e)))))

(defn ordered-query-result
  ([method endpoint] (ordered-query-result method endpoint nil))
  ([method endpoint query] (ordered-query-result method endpoint query {}))
  ([method endpoint query params & optional-handlers]
   (let [handlers (or optional-handlers [identity])
         handle-fn (apply comp (vec handlers))
         response (query-response method endpoint query params)]
     (is (= HttpURLConnection/HTTP_OK (:status response)))
     (handle-fn (convert-response response)))))

(defn query-result
  ([method endpoint] (query-result method endpoint nil))
  ([method endpoint query] (query-result method endpoint query {}))
  ([method endpoint query params & optional-handlers]
   (testing (str "Running query " query)
     (apply #(ordered-query-result method endpoint query params set %)
            (or optional-handlers [identity])))))

(defn internal-request
  "Create a ring request as it would look after passing through all of the
   application middlewares, suitable for invoking one of the api functions
   (where it assumes the middleware have already assoc'd in various attributes)."
  ([]
     (internal-request {}))
  ([params]
     (internal-request {} params))
  ([global-overrides params]
     {:params params
      :headers {"accept" "application/json"
                "content-type" "application/x-www-form-urlencoded"}
      :content-type "application/x-www-form-urlencoded"
      :globals (merge {:update-server "FOO"
                       :scf-read-db          *db*
                       :scf-write-dbs        [*db*]
                       :scf-write-db-names   ["default"]
                       :product-name         "puppetdb"
                       :add-agent-report-filter true
                       :node-purge-ttl       (t/parse-period "14d")
                       :log-queries          false}
                      global-overrides)}))

(defn internal-request-post
  "A variant of internal-request designed to submit application/json requests
  instead."
  ([body]
     (internal-request-post body {}))
  ([body params]
     {:params params
      :headers {"accept" "application/json"
                "content-type" "application/json"}
      :content-type "application/json"
      :globals {:update-server "FOO"
                :scf-read-db          *db*
                :scf-write-dbs        [*db*]
                :scf-write-db-names   ["default"]
                :product-name         "puppetdb"}
      :body (ByteArrayInputStream. (.getBytes body "utf8"))}))

(defn call-with-http-app
  "Builds an HTTP app and make it available as *app* during the
  execution of (f).  Calls (adjust-globals default-globals) if
  adjust-globals is provided."
  ([f] (call-with-http-app f identity))
  ([f adjust-globals]
   (let [get-shared-globals #(adjust-globals {:scf-read-db *read-db*
                                              :scf-write-dbs [*db*]
                                              :scf-write-db-names ["default"]
                                              :url-prefix ""
                                              :add-agent-report-filter true
                                              :node-purge-ttl (t/parse-period "14d")
                                              :log-queries false})]
     (binding [*app* (wrap-with-puppetdb-middleware
                      (server/build-app get-shared-globals))]
       (f)))))

(defmacro with-http-app*
  [adjust-globals & body]
  `(call-with-http-app (fn [] ~@body) ~adjust-globals))

(defmacro with-http-app
  [& body]
  `(call-with-http-app (fn [] ~@body)))

(defmacro deftest-http-app [name bindings & body]
  `(deftest ~name
     (tu/dotestseq ~bindings
       (with-test-db
         (with-http-app ~@body)))))
