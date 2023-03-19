(ns rems.jwt
  (:require [buddy.core.codecs :as codecs]
            [buddy.core.codecs.base64 :as b64]
            [buddy.core.keys :as buddy-keys]
            [buddy.sign.jwe :as buddy-jwe]
            [buddy.sign.jwt :as buddy-jwt]
            [buddy.core.hash :as buddy-hash]
            [clojure.java.io :as io]
            [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [clj-http.client :as http]
            [clojure.core.memoize]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [rems.common.util :refer [getx index-by]]
            [rems.config :refer [env oidc-configuration]]
            [rems.json :as json]))

;; Could consider caching this if it is a performance bottleneck.
;; However our OIDC login already has like 3 roundtrips to the OIDC
;; server so one more won't hurt that much. We will need to fetch new
;; keys occasionally in case REMS is running over an OIDC key
;; rotation.
(defn- fetch-jwks []
  (when-let [jwks-uri (:jwks_uri oidc-configuration)]
    (getx (http/get jwks-uri {:as :json}) :body)))

(defn- indexed-jwks []
  (index-by [:kid] (getx (fetch-jwks) :keys)))

(defn- fetch-public-key [jwt]
  (let [key-id (:kid (buddy-jwe/decode-header jwt))
        jwk (getx (indexed-jwks) key-id)]
    (buddy-keys/jwk->public-key jwk)))

(defn- fetch-jku-jwks [jku]
  (getx (http/get jku {:as :json}) :body))

(defn- indexed-jku-jwks [jku]
  (when jku
    (index-by [:kid] (getx (fetch-jku-jwks jku) :keys))))

(def memoized-indexed-jku-jwks
  (clojure.core.memoize/ttl indexed-jku-jwks :ttl/threshold 60000)) ; cache for 1 minute

(defn- fetch-visa-public-key [visa]
  (let [decoded-visa (buddy-jwe/decode-header visa)
        key-id (:kid decoded-visa)
        jwk (getx (memoized-indexed-jku-jwks (:jku decoded-visa)) key-id)]
    (buddy-keys/jwk->public-key jwk)))

(defn sign [claims secret & [opts]]
  (buddy-jwt/sign claims secret opts))

(defn validate-visa [visa now]
  (let [public-key (fetch-visa-public-key visa)]
    (buddy-jwt/unsign visa public-key {:alg :rs256
                                       :now now
                                       :iss (:iss visa)})))

(defn show
  "Show the claims of a JWT token without verifying anything."
  [jwt]
  (->> (str/split jwt #"\." 2) ; 2 because we don't care about the signature
       (mapv (fn [s] (.getBytes s "UTF-8")))
       (mapv b64/decode)
       (mapv codecs/bytes->str)
       (mapv json/parse-string)))

(deftest test-show
  (is (= [{:alg "RS256"
           :kid "2011-04-29"
           :jku "http://localhost:3000/api/jwk"
           :typ "JWT"}
          {:sub "elixir-user"
           :iss "http://localhost:3000/"
           :exp 2556144000
           :ga4gh_visa_v1 {:value "EGAD00001006673"
                           :type "ControlledAccessGrants"
                           :source "EGAC00001000908"
                           :asserted 1034294400
                           :by "dac"}
           :iat 2524608000}]
         (show "eyJhbGciOiJSUzI1NiIsImprdSI6Imh0dHA6Ly9sb2NhbGhvc3Q6MzAwMC9hcGkvandrIiwidHlwIjoiSldUIiwia2lkIjoiMjAxMS0wNC0yOSJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjMwMDAvIiwic3ViIjoiZWxpeGlyLXVzZXIiLCJpYXQiOjI1MjQ2MDgwMDAsImV4cCI6MjU1NjE0NDAwMCwiZ2E0Z2hfdmlzYV92MSI6eyJ0eXBlIjoiQ29udHJvbGxlZEFjY2Vzc0dyYW50cyIsInZhbHVlIjoiRUdBRDAwMDAxMDA2NjczIiwic291cmNlIjoiRUdBQzAwMDAxMDAwOTA4IiwiYnkiOiJkYWMiLCJhc3NlcnRlZCI6MTAzNDI5NDQwMH19.LnfsNxVfM_NfuxYYQtZexp975Hc3hrCxTG0fhMrgTakSLXa6gASc5MPn14seqsTjuyhtmUnu7WrCEVxko8WRvJybGDWmdbrycYafNg4amevtbs7hTPCkqAXD1DcuP53LDeLhSl_YrNgfz4aDE0uaw37I8TAsqdAeDALcZqQ6SIwF5wBG_wRWtKTPmDp-GTpzy9STx-nrIqw3SYeftunlI4wDs5avaktDuOpgMl8TVUGodGFjJsZjN8UOhKgSsGdXDGmu4FeeIjJt9Sa_dsCQPZQ1GpHyg1lFa63FZPPOy2-F9TNZcHJR1vFxKLD9U8Lvr11-EFjIiGuDg6miiWyodw"))))

(defn validate [jwt issuer audience now]
  (when (:log-authentication-details env)
    (log/debug "JWT: " (show jwt)))
  (let [public-key (fetch-public-key jwt)]
    (buddy-jwt/unsign jwt public-key (merge {:alg :rs256
                                             :now now}
                                            (when issuer {:iss issuer})
                                            (when audience {:aud audience})))))

;;(defn sha256-hashed-encryption-secret [] (buddy-hash/sha256 "cadre-secret-for-hashing"))
;;(defn sha512-hashed-encryption-secret [] (buddy-hash/sha512 "cadre-secret-for-hashing"))

(defn get-file-absolute-path [relative-path]
  (.getAbsolutePath (io/file relative-path)))

;;Read public and private keys from respective files, for use of asymetric algorithm in encryption and decyption process
(def pubkey-file-path (get-file-absolute-path "encryption-pubkey.pem"))
(def pubkey-file-object (io/file pubkey-file-path))

(defn encryption-pubkey []
  (if (.exists pubkey-file-object)
    (do
      (log/info "Public key File exists.")
      (log/info "Current working directory: " (System/getProperty "user.dir"))
      (let [current-dir (java.io.File. ".")]
        (log/info "java.io.File. ==== " (.getAbsolutePath current-dir)))
        ;;(buddy-keys/public-key (get-file-absolute-path "encryption-pubkey.pem"))
          (buddy-keys/public-key "/home/310/vc8580/rems/env/dev/resources/encryption-pubkey.pem"))
    (do
      (log/info "Public key File does not exist.")
      (log/info "Current working directory: " (System/getProperty "user.dir"))
      (let [current-dir (java.io.File. ".")]
        (log/info "java.io.File. ==== " (.getAbsolutePath current-dir)))
      
      ;;(let [file (java.io.File. "xxxxx.txt")] (.delete file) (.createNewFile file)) 

      ;;return
      ;;(buddy-keys/public-key (get-file-absolute-path "encryption-pubkey.pem"))
      (buddy-keys/public-key "/home/310/vc8580/rems/env/dev/resources/encryption-pubkey.pem")
      ))
  
  )

(def privkey-file-path (get-file-absolute-path "encryption-privkey.pem"))
(def privkey-file-object (io/file privkey-file-path))

(defn encryption-privkey []
  (if (.exists privkey-file-object)
    (do
      (log/info "Private key File exists.") 
      (let [current-dir (java.io.File. ".")]
        (log/info "java.io.File. ==== " (.getAbsolutePath current-dir)))
      ;;(buddy-keys/private-key (privkey-file-path "encryption-privkey.pem") "cadre-encryption-privkey")
      (buddy-keys/private-key (privkey-file-path "/home/310/vc8580/rems/env/dev/resources/encryption-privkey.pem") "cadre-encryption-privkey")
      )
    (do
      (log/info "Private key File does not exist.")
      (log/info "Current working directory: " (System/getProperty "user.dir"))
      (let [current-dir (java.io.File. ".")]
        (log/info "java.io.File. ==== " (.getAbsolutePath current-dir)))
      (buddy-keys/private-key (privkey-file-path "/home/310/vc8580/rems/env/dev/resources/encryption-privkey.pem") "cadre-encryption-privkey")
      )
    ))

(defn encrypt-data [payload]
  ;; Hash your secret key with sha256 by create a byte array of 32 bytes because
  ;; because it is a requirement for default content encryption algorithm: DIR
  (let [encrypted-data (buddy-jwt/encrypt payload (encryption-pubkey) {:alg :rsa-oaep-256 :enc :a256cbc-hs512})]
  ;;(let [encrypted-data (buddy-jwt/encrypt payload (sha256-hashed-encryption-secret) {:alg :dir :enc :a128cbc-hs256})]  
  (log/info "Encrypted Data: " (str encrypted-data))
    encrypted-data))

(defn decrypt-data [encrypted-data]
   (log/info "Unsigned Token: " (str :header encrypted-data))
   (let [decrypted-data (buddy-jwt/decrypt encrypted-data (encryption-privkey) {:alg :rsa-oaep-256 :enc :a256cbc-hs512})]
   ;;(let [decrypted-data (buddy-jwt/decrypt encrypted-data (sha256-hashed-encryption-secret))]  
        (log/info "Decrypted Data: " (str decrypted-data))
        decrypted-data))

(comment

;;Read public and private keys from respective file, for use of asymetric algorithm in signing and unsigning process
(def signing-privkey (buddy-keys/private-key "signing-privkey.pem" "cadre-signing-privkey"))
(def signing-pubkey (buddy-keys/public-key "signing-pubkey.pem"))

(defn sign-token [encrypted-data]
  (buddy-jwt/sign {:encrypted-data encrypted-data} signing-privkey {:alg :rs512}))

;; Function "unsign-token" accepts JWT (signed & encrypted JWT),
;;and returns unsigned Encrypted data.
(defn unsign-token [signed-jwt]
  ;; use timestamp in the past
  (buddy-jwt/unsign signed-jwt signing-pubkey {:alg :rs512}))
)