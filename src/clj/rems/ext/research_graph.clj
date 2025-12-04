(ns rems.ext.research-graph
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [rems.config :refer [env]]
            [rems.json :as json]
            [rems.util :refer [getx]]))

(defn orcid-details-from-research-graph [orcid]
  (try
    (let [url (str (getx env :rg-augment-api-url)
                   orcid
                   "?apikey=" (getx env :rg-augment-api-key))
          response (client/get url {:accept :json})]
      (if (= 200 (:status response))
        (json/parse-string (:body response))
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking Research Graph API - " (.getMessage e)))))