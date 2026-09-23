(ns rems.service.cadre.placeholders
  (:require [clojure.string :as str]))

(def ^:private placeholder-regex
  #"\%\{([^}]+)\}")

(defmulti resolve-placeholder
  "Dispatch on the placeholder key to resolve its value.
   Extend this multimethod to add support for new placeholders.
   Each method receives the placeholder key and context map,
   and returns the replacement string, or nil if no replacement should be made."
  (fn [placeholder-key _context]
    placeholder-key))

(defn- apply-placeholder
  "Apply a single placeholder replacement to the text."
  [text [_ placeholder-key] context]
  (if-let [handler (get-method resolve-placeholder placeholder-key)]
    (if-let [replacement (handler placeholder-key context)]
      (str/replace text (str "%{" placeholder-key "}") replacement)
      text)
    text))

(defn replace-placeholders
  "Replace all placeholders in the text using the provided context map.
    Placeholders are in the format %{key} where key is matched against
    registered resolve-placeholder handlers.
    Returns the text with all known placeholders replaced."
  [text context]
  (if (nil? text)
    text
    (reduce #(apply-placeholder %1 %2 context) text (re-seq placeholder-regex text))))

(defmethod resolve-placeholder "ticket.name.first"
  [_key context]
  (when-let [application (:application context)]
    (when-let [applicant (:application/applicant application)]
      (when-let [name (:name applicant)]
        (first (str/split name #"\s+"))))))

(defmethod resolve-placeholder "ticket.number"
  [_key context]
  (when-let [appid (:appid context)]
    (str appid)))

(defn resolve-cannedresponse-placeholders
  "Replace placeholders in a canned response using the provided context map.
   Currently supported placeholders:
   - %{ticket.name.first} - First name of the applicant
   - %{ticket.number} - Application ID
   The context must contain :application/applicant (for applicant name)
   and/or :appid (for ticket number)."
  [response context]
  (replace-placeholders response context))
