(ns rems.poller.entitlements
  "Handing out entitlements for accepted applications. Stores
   entitlements in the db and optionally POSTs them to a webhook."
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.api.applications-v2 :as applications-v2]
            [rems.db.entitlements :as entitlements]
            [rems.poller.common :as common]
            [rems.scheduler :as scheduler])
  (:import [org.joda.time Duration]))

(defn- entitlements-for-event [event]
  ;; we filter by event here, and by state in update-entitlements-for.
  ;; this is for performance reasons only
  (when (contains? #{:application.event/approved
                     :application.event/licenses-accepted
                     :application.event/member-removed
                     :application.event/closed}
                   (:event/type event))
    (let [application (applications-v2/get-unrestricted-application (:application/id event))]
      (entitlements/update-entitlements-for application))))

(defn run []
  (common/run-event-poller ::poller entitlements-for-event))

(mount/defstate entitlements-poller
  :start (scheduler/start! run (Duration/standardSeconds 10))
  :stop (scheduler/stop! entitlements-poller))
