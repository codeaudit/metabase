(ns metabase.query-processor.util
  "Utility functions used by the global query processor and middleware functions."
  (:require (buddy.core [codecs :as codecs]
                        [hash :as hash])
            [cheshire.core :as json]
            [toucan.db :as db]
            [metabase.models.query-execution :refer [QueryExecution]]))

(defn mbql-query?
  "Is the given query an MBQL query?"
  [query]
  (= :query (keyword (:type query))))

(defn datetime-field?
  "Is FIELD a `DateTime` field?"
  [{:keys [base-type special-type]}]
  (or (isa? base-type :type/DateTime)
      (isa? special-type :type/DateTime)))

(defn query-without-aggregations-or-limits?
  "Is the given query an MBQL query without a `:limit`, `:aggregation`, or `:page` clause?"
  [{{aggregations :aggregation, :keys [limit page]} :query}]
  (and (not limit)
       (not page)
       (or (empty? aggregations)
           (= (:aggregation-type (first aggregations)) :rows))))

(defn query->remark
  "Genarate an approparite REMARK to be prepended to a query to give DBAs additional information about the query being executed.
   See documentation for `mbql->native` and [issue #2386](https://github.com/metabase/metabase/issues/2386) for more information."
  ^String [{{:keys [executed-by uuid query-hash query-type], :as info} :info}]
  (format "Metabase:: userID: %s executionID: %s queryType: %s queryHash: %s" executed-by uuid query-type (codecs/bytes->hex query-hash)))


;;; ------------------------------------------------------------ Hashing ------------------------------------------------------------

(defn- select-keys-for-hashing
  "Return QUERY with only the keys relevant to hashing kept.
   (This is done so irrelevant info or options that don't affect query results doesn't result in the same query producing different hashes.)"
  [query]
  {:pre [(map? query)]}
  (select-keys query [:database :type :query :parameters :constraints]))

(defn query-hash
  "Return a 256-bit SHA3 hash of QUERY as a key for the cache. (This is returned as a byte array.)"
  [query]
  (hash/sha3-256 (json/generate-string (select-keys-for-hashing query))))


;;; ------------------------------------------------------------ Historic Duration Info ------------------------------------------------------------

(defn query-average-duration
  "Return the average running time of QUERY over the last 10 executions in milliseconds.
   Returns `nil` if there's not available data."
  ^Float [query]
  (when-let [running-times (db/select-field :running_time QueryExecution
                             :hash (query-hash query)
                             {:order-by [[:started_at :desc]]
                              :limit    10})]
    (float (/ (reduce + running-times)
              (count running-times)))))
