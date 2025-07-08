(ns rems.form-validation
  "Pure functions for form validation logic"
  (:require [clojure.string :as str]
            [rems.common.form :as form]
            [rems.common.util :refer [+email-regex+
                                      +phone-number-regex+
                                      +ipv4-regex+
                                      +ipv6-regex+
                                      +reserved-ipv4-range-regex+
                                      +reserved-ipv6-range-regex+]]))

(def iso-alpha2-countries
  #{"AF" "AX" "AL" "DZ" "AS" "AD" "AO" "AI" "AQ" "AG" "AR" "AM" "AW" "AU"
    "AT" "AZ" "BS" "BH" "BD" "BB" "BY" "BE" "BZ" "BJ" "BM" "BT" "BO" "BA" 
    "BW" "BV" "BR" "IO" "BN" "BG" "BF" "BI" "CV" "KH" "CM" "CA" "KY" "CF" 
    "TD" "CL" "CN" "CX" "CC" "CO" "KM" "CD" "CG" "CK" "CR" "CI" "HR" "CU" 
    "CW" "CY" "CZ" "DK" "DJ" "DM" "DO" "EC" "EG" "SV" "GQ" "ER" "EE" "SZ" 
    "ET" "FK" "FO" "FJ" "FI" "FR" "GF" "PF" "TF" "GA" "GM" "GE" "DE" "GH" 
    "GI" "GR" "GL" "GD" "GP" "GU" "GT" "GG" "GN" "GW" "GY" "HT" "HM" "VA" 
    "HN" "HK" "HU" "IS" "IN" "ID" "IR" "IQ" "IE" "IM" "IL" "IT" "JM" "JP" 
    "JE" "JO" "KZ" "KE" "KI" "KP" "KR" "KW" "KG" "LA" "LV" "LB" "LS" "LR" 
    "LY" "LI" "LT" "LU" "MO" "MK" "MG" "MW" "MY" "MV" "ML" "MT" "MH" "MQ" 
    "MR" "MU" "YT" "MX" "FM" "MD" "MC" "MN" "ME" "MS" "MA" "MZ" "MM" "NA" 
    "NR" "NP" "NL" "NC" "NZ" "NI" "NE" "NG" "NU" "NF" "MP" "NO" "OM" "PK" 
    "PW" "PS" "PA" "PG" "PY" "PE" "PH" "PN" "PL" "PT" "PR" "QA" "RE" "RO" 
    "RU" "RW" "BL" "SH" "KN" "LC" "MF" "PM" "VC" "WS" "SM" "ST" "SA" "SN" 
    "RS" "SC" "SL" "SG" "SX" "SK" "SI" "SB" "SO" "ZA" "GS" "SS" "ES" "LK" 
    "SD" "SR" "SJ" "SE" "CH" "SY" "TW" "TJ" "TZ" "TH" "TL" "TG" "TK" "TO" 
    "TT" "TN" "TR" "TM" "TC" "TV" "UG" "UA" "AE" "GB" "US" "UM" "UY" "UZ" 
    "VU" "VE" "VN" "VG" "VI" "WF" "EH" "YE" "ZM" "ZW"})

(defn- required-error [field]
  (let [field-value (:field/value field)]
    (when (and (:field/visible field)
               (not (:field/optional field)))
      (case (:field/type field)
        (:header :label) nil
        :table ;; a non-optional table must have at least one row
        (when (empty? field-value)
          {:field-id (:field/id field)
           :type :t.form.validation/required})
        ;; default:
        (when (cond
                (string? field-value) (str/blank? field-value)
                :else (nil? field-value))
          {:field-id (:field/id field)
           :type :t.form.validation/required})))))

(defn- too-long-error [field]
  (when-let [limit (:field/max-length field)]
    (when (> (count (:field/value field)) limit)
      {:field-id (:field/id field)
       :type     :t.form.validation/toolong})))

(defn- invalid-email-address-error [field]
  (when (= (:field/type field) :email)
    (when-not (or (str/blank? (:field/value field))
                  (re-matches +email-regex+ (:field/value field)))
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-email})))

(defn- invalid-phone-number-error [field]
  (when (= (:field/type field) :phone-number)
    (when-not (or (str/blank? (:field/value field))
                  (re-matches +phone-number-regex+ (:field/value field)))
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-phone-number})))

(defn- invalid-ip-address-error [field]
  (when (and (= (:field/type field) :ip-address)
             (not (str/blank? (:field/value field))))
    (let [matches #(first (re-matches % (:field/value field)))
          invalid-ip? (not-any? matches [+ipv4-regex+ +ipv6-regex+])
          private-ip? (or (every? matches [+ipv4-regex+ +reserved-ipv4-range-regex+])
                          (every? matches [+ipv6-regex+ +reserved-ipv6-range-regex+]))]
      (or (when invalid-ip? {:field-id (:field/id field)
                             :type :t.form.validation/invalid-ip-address})
          (when private-ip? {:field-id (:field/id field)
                             :type :t.form.validation/invalid-ip-address-private})))))

(defn- invalid-country-error [field]
  (when (and (= (:field/type field) :country)
             (not (str/blank? (:field/value field))))
    (if-not (contains? iso-alpha2-countries (clojure.string/upper-case (:field/value field)))
      {:field-id (:field/id field)
        :type :t.form.validation/invalid-country})))

(defn- option-value-valid? [field]
  (let [allowed-values (set (conj (map :key (:field/options field)) ""))]
    (contains? allowed-values (:field/value field))))

(defn- invalid-option-error [field]
  (when (= (:field/type field) :option)
    (when-not (option-value-valid? field)
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-value})))

(defn- multiselect-value-valid? [field]
  (let [allowed? (set (conj (map :key (:field/options field)) ""))]
    (every? allowed? (form/parse-multiselect-values (:field/value field)))))

(defn- invalid-multiselect-error [field]
  (when (= (:field/type field) :multiselect)
    (when-not (multiselect-value-valid? field)
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-value})))

(defn- missing-columns-error [field]
  (when (= (:field/type field) :table)
    (let [columns (set (map :key (:field/columns field)))
          row-ok? (fn [row] (= columns (set (map :column row))))
          columns-set? (fn [row] (not-any? (comp str/blank? :value) row))
          value (:field/value field)]
      ;; Schema validation guarantees that it's either a s/Str or
      ;; a [[{:column s/Str :value s/Str}]], and we've ruled out s/Str
      ;; in wrong-value-type-error
      (or (when-not (every? row-ok? value)
            {:field-id (:field/id field)
             :type :t.form.validation/invalid-value})
          (when-not (every? columns-set? value)
            {:field-id (:field/id field)
             :type :t.form.validation/column-values-missing})))))

;; TODO: validate that attachments are actually valid?
(defn- invalid-attachment-error [field]
  (when (= (:field/type field) :attachment)
    (when-not (every? number? (form/parse-attachment-ids (:field/value field)))
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-value})))

(defn- wrong-value-type-error [field]
  (let [value (:field/value field)]
    (case (:field/type field)
      :table
      (when-not (sequential? value)
        {:field-id (:field/id field)
         :type :t.form.validation/invalid-value})

      ;; default
      (when-not (or (nil? (:field/value field))
                    (string? (:field/value field)))
        {:field-id (:field/id field)
         :type :t.form.validation/invalid-value}))))

(defn- validate-field-content [field]
  (or (wrong-value-type-error field)
      (invalid-email-address-error field)
      (invalid-phone-number-error field)
      (invalid-ip-address-error field)
      (invalid-country-error field)
      (too-long-error field)
      (invalid-option-error field)
      (invalid-multiselect-error field)
      (missing-columns-error field)
      (invalid-attachment-error field)))

(defn- validate-field [field]
  (or (required-error field)
      (validate-field-content field)))

(defn validate-fields [fields]
  (->> (sort-by :field/id fields)
       (keep validate-field)
       not-empty))

