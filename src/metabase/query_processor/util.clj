(ns metabase.query-processor.util
  "Utility functions used by the global query processor and middleware functions."
  (:require [buddy.core.hash :as hash]))

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
  (format "Metabase:: userID: %s executionID: %s queryType: %s queryHash: %s" executed-by uuid query-type query-hash))


;;; ------------------------------------------------------------ Hashing ------------------------------------------------------------

;; There are two ways we hash queries: the O.G. `legacy-query-hash` function which is basically a thin wrapper around `clojure.core/hash`, and a cryptographically-secure
;; SHA3 256-bit `secure-query-hash`.

(defn- select-keys-for-hashing
  "Return QUERY with only the keys relevant to hashing kept.
   (This is done so irrelevant info or options that don't affect query results doesn't result in the same query producing different hashes.)"
  [query]
  {:pre [(map? query)]}
  (select-keys query [:database :type :query :parameters :constraints]))

(defn ^:deprecated legacy-query-hash
  "A non-cryptographic hash of QUERY, returned as an Integer."
  ^Integer [query]
  (hash (select-keys-for-hashing query)))

(defn secure-query-hash
  "Return a 256-bit SHA3 hash of QUERY as a key for the cache. (This is returned as a byte array.)"
  [query]
  (hash/sha3-256 (str (select-keys-for-hashing query))))
