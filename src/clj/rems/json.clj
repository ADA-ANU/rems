(ns rems.json
  (:require [clojure.test :refer [deftest is testing]]
            [cognitect.transit :as transit]
            [cuerdas.core :refer [numeric? parse-number]]
            [jsonista.core :as j]
            [muuntaja.core :as muuntaja])
  (:import [com.fasterxml.jackson.datatype.joda JodaModule]
           [org.joda.time DateTime ReadableInstant DateTimeZone]
           [org.joda.time.format ISODateTimeFormat]))

(def joda-time-writer
  (transit/write-handler
   "t"
   (fn [v] (-> (ISODateTimeFormat/dateTime) (.print ^ReadableInstant v)))))

(def muuntaja
  (muuntaja/create
   (-> muuntaja/default-options
       (assoc-in [:formats "application/json" :encoder-opts :modules] [(JodaModule.)])
       (assoc-in [:formats "application/transit+json" :encoder-opts :handlers] {DateTime joda-time-writer}))))

;; Sometimes we have ints as keys in clj maps, which are stringified in JSON
(defn- str->keyword-or-number [str]
  (if (numeric? str)
    (parse-number str)
    (keyword str)))

(def mapper
  (j/object-mapper
   {:modules [(JodaModule.)]
    :decode-key-fn str->keyword-or-number}))

(defn generate-string [obj]
  (j/write-value-as-string obj mapper))

(defn parse-string [json]
  (j/read-value json mapper))

(deftest test-muuntaja
  (let [format "application/json"]
    (testing format
      (testing "encoding"
        (is (= "{\"date-time\":\"2000-01-01T12:00:00.000Z\"}"
               (slurp (muuntaja/encode muuntaja format {:date-time (DateTime. 2000 1 1 12 0 DateTimeZone/UTC)}))))
        (is (= "{\"date-time\":\"2000-01-01T10:00:00.000Z\"}"
               (slurp (muuntaja/encode muuntaja format {:date-time (DateTime. 2000 1 1 12 0 (DateTimeZone/forID "Europe/Helsinki"))})))))

      (testing "decoding"
        ;; decoding dates from JSON requires coercion, so it's passed through as just plain string
        (is (= {:date-time "2000-01-01T10:00:00.000Z"}
               (muuntaja/decode muuntaja format "{\"date-time\":\"2000-01-01T10:00:00.000Z\"}"))))))

  (let [format "application/transit+json"]
    (testing format
      (testing "encoding"
        (is (= "[\"^ \",\"~:date-time\",\"~t2000-01-01T12:00:00.000Z\"]"
               (slurp (muuntaja/encode muuntaja format {:date-time (DateTime. 2000 1 1 12 0 DateTimeZone/UTC)}))))
        (is (= "[\"^ \",\"~:date-time\",\"~t2000-01-01T12:00:00.000+02:00\"]"
               (slurp (muuntaja/encode muuntaja format {:date-time (DateTime. 2000 1 1 12 0 (DateTimeZone/forID "Europe/Helsinki"))})))))

      (testing "decoding"
        (is (= {:date-time (.toDate (DateTime. 2000 1 1 12 0 DateTimeZone/UTC))}
               (muuntaja/decode muuntaja format "[\"^ \",\"~:date-time\",\"~t2000-01-01T12:00:00.000Z\"]")))
        (is (= {:date-time (.toDate (DateTime. 2000 1 1 12 0 DateTimeZone/UTC))}
               (muuntaja/decode muuntaja format "[\"^ \",\"~:date-time\",\"~m946728000000\"]")))))))
