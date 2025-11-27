(ns rems.service.cadre.parse-users
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(def allowed-content-types
  #{"text/csv" "application/vnd.ms-excel" "application/csv"})

(defn normalize-header-term [term]
  (let [lower-case-term (str/lower-case term)]
    (cond
      (or (= lower-case-term "email")
          (= lower-case-term "email address"))
      :email

      (or (= lower-case-term "name")
          (= lower-case-term "first name"))
      :first-name

      (= lower-case-term "last name")
      :last-name

      :else
      :unknown)))

(defn normalize-header [header]
  (mapv normalize-header-term header))

(defn valid-csv-header? [header]
  (let [normalized (normalize-header header)]
    (or (= normalized [:first-name :last-name :email])
        (= normalized [:first-name :email])
        (= normalized [:name :email]))))

(defn parse-csv [input-stream]
  (with-open [reader (io/reader input-stream)]
    (let [rows (doall (csv/read-csv reader))
          header (first rows)
          body (rest rows)
          normalized-header (normalize-header header)]
      (when-not (valid-csv-header? header)
        (throw (ex-info "Invalid CSV header" {:header header})))
      {:normalized-header normalized-header
       :rows body})))

(defn process-rows [{:keys [normalized-header rows]}]
  (case normalized-header
    [:first-name :last-name :email]
    (mapv (fn [[first last email]]
            {:name (str first " " last)
             :email email})
          rows)

    [:first-name :email]
    (mapv (fn [[name email]]
            {:name name
             :email email})
          rows)))

(defn upload-handler [file]
  (when (nil? file)
    {:error "Missing file upload"})

  (when (not (contains? allowed-content-types (:content-type file)))
    {:error "Invalid content type"})

  (try
    (let [parsed (parse-csv (:tempfile file))
          results (process-rows parsed)]
      results)
    (catch Exception e
      {:error (.getMessage e)})))