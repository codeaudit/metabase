(ns metabase.models.query-execution
  (:require [schema.core :as s]
            [toucan.models :as models]
            [metabase.util :as u]))


(models/defmodel QueryExecution :query_queryexecution)

(def Context
  "Schema for valid values of QueryExecution `:context`."
  (s/enum :ad-hoc
          :csv-download
          :dashboard
          :embedded-dashboard
          :embedded-question
          :json-download
          :map-tiles
          :public-dashboard
          :public-question
          :pulse
          :question))

(defn- pre-insert [{context :context, :as query-execution}]
  (u/prog1 query-execution
    (s/validate Context context)))

(defn- post-select [{:keys [result_rows] :as query-execution}]
  ;; sadly we have 2 ways to reference the row count :(
  (assoc query-execution :row_count (or result_rows 0)))

(u/strict-extend (class QueryExecution)
  models/IModel
  (merge models/IModelDefaults
         {:default-fields (constantly [:id :uuid :json_query :context :status :started_at :finished_at :running_time :error :result_rows])
          :types          (constantly {:json_query :json, :status :keyword, :context :keyword, :error :clob})
          :pre-insert     pre-insert
          :post-select    post-select}))
