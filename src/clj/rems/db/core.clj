(ns rems.db.core
  (:require [clj-time.core :as time]
            [clj-time.jdbc] ;; convert db timestamps to joda-time objects
            [clojure.set :refer [superset?]]
            [conman.core :as conman]
            [rems.env :refer [*db*]]))

(conman/bind-connection *db* "sql/queries.sql")

(defn assert-test-database! []
  (assert (= {:current_database "rems_test"}
             (get-database-name))))

(defn contains-all-kv-pairs? [supermap map]
  (superset? (set supermap) (set map)))


(defn now-active? [start end]
  (and (or (nil? start)
           (time/after? (time/now) start))
       (or (nil? end)
           (time/before? (time/now) end))))

(defn assoc-active
  "Calculates and assocs :active? attribute based on current time and :start and :endt attributes."
  [x]
  (assoc x :active? (now-active? (:start x)(:endt x))))
