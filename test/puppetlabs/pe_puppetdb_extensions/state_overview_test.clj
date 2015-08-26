(ns puppetlabs.pe-puppetdb-extensions.state-overview-test
  (:require [clojure.test :refer :all]
            [clj-time.core :refer [now]]
            [puppetlabs.puppetdb.testutils.services :as svcs]
            [puppetlabs.pe-puppetdb-extensions.testutils :as utils
             :refer [get-json with-puppetdb-instance blocking-command-post]]
            [puppetlabs.puppetdb.examples.reports :refer [reports]]
            [puppetlabs.puppetdb.testutils.reports :as tur]
            [puppetlabs.puppetdb.reports :as reports]
            [puppetlabs.puppetdb.testutils.log
             :refer [with-log-suppressed-unless-notable notable-pdb-event?]]))

(defn get-munged-report [report]
  (tur/munge-example-report-for-storage (get reports report)))

(deftest query-state-overview
  (with-log-suppressed-unless-notable notable-pdb-event?
    (with-puppetdb-instance (utils/pdb1-sync-config)
      (let [report (get-munged-report :basic)
            report2 (-> (get-munged-report :basic2)
                        (merge {:certname "bar.local" :end_time (now)}))]
        (blocking-command-post (utils/pdb-cmd-url) "store report" 5 report)
        (blocking-command-post (utils/pdb-cmd-url) "store report" 5 report2)
        (testing "query with no parameters returns correct counts"
          (let [actual (get-json (utils/pe-pdb-url) "/state-overview")
                expected {:unchanged 0
                          :failed 0
                          :noop 1
                          :unresponsive 1
                          :changed 0
                          :unreported 0}]
            (is (= expected actual))))
        (testing "query with unresponsive_threshold correctly changes counts"
          (do
            ;; Wait a little while so we can test unresponsive threshold
            (Thread/sleep 1000)
            (let [actual (get-json (utils/pe-pdb-url) (format "/state-overview?unresponsive_threshold=1" ))
                  ;; Wait a little while so we can test unresponsive threshold
                  expected {:unchanged 0
                            :failed 0
                            :noop 0
                            :unresponsive 2
                            :changed 0
                            :unreported 0}]
              (is (= expected actual)))))

        (testing "invalid url returns 404"
          (let [status (-> (utils/pe-pdb-url)
                           (utils/get-response "/fbar" {:throw-exceptions false})
                           :status)]
            (is (= 404 status))))

        (testing "state-overview excludes deactivated nodes"
          (do
            (blocking-command-post (utils/pdb-cmd-url) "deactivate node" 3 {"certname" "bar.local"})
            ;; Sleep to allow status to change to unresponsive with threshold=1
            (Thread/sleep 1000)
            (let [actual (get-json (utils/pe-pdb-url) (format "/state-overview?unresponsive_threshold=1"))
                  expected {:unchanged 0
                            :failed 0
                            :noop 0
                            :unresponsive 1
                            :changed 0
                            :unreported 0}]
              (is (= expected actual)))))))))
