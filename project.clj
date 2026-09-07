(defproject rems "2.32"
  :description "Resource Entitlement Management System is a tool for managing access rights to resources, such as research datasets."
  :url "https://github.com/CSCfi/rems"

  :dependencies [[buddy/buddy-core "1.12.0-430"]
                 [buddy/buddy-auth "3.0.323"]
                 [buddy/buddy-sign "3.6.1-359"]
                 [ch.qos.logback/logback-classic "1.6.3"]
                 [clj-http "3.13.1"]
                 [cheshire "6.2.0" :exclusions [com.fasterxml.jackson.core/jackson-core]] ; clj-http uses cheshire's json parsing
                 [clj-pdf "2.8.1"]
                 [clj-time "0.15.2"]
                 [com.attendify/schema-refined "0.3.0-alpha5"]
                 [com.draines/postal "2.0.5"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-joda "2.22.2"]
                 [com.stuartsierra/dependency "1.0.0"]
                 [com.rpl/specter "1.1.6"]
                 [com.taoensso/tempura "1.5.4"]
                 [compojure "1.7.2"]
                 [conman "0.9.6"]
                 [cprop "0.1.21"]
                 [funcool/cuerdas "2026.415"]
                 [garden "1.3.10"]
                 [hiccup "2.0.0"]
                 [com.cognitect/transit-clj "1.1.363"]
                 [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]
                 [lambdaisland/deep-diff "0.0-47"]
                 [luminus-jetty "0.2.3"]
                 [luminus-migrations "0.7.5"]
                 [luminus-nrepl "0.1.7"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [macroz/hiccup-find "0.6.1"]
                 [markdown-clj "1.12.10"]
                 [medley "1.4.0"]
                 [metosin/compojure-api "2.0.0-alpha30" :exclusions [cheshire com.fasterxml.jackson.core/jackson-core]]
                 [metosin/jsonista "1.0.1"]
                 [metosin/ring-swagger "1.1.0"]
                 [metosin/ring-swagger-ui "5.32.11"]
                 [mount "0.1.24"]
                 [ns-tracker "1.0.0"]
                 [org.apache.lucene/lucene-core "10.5.1"]
                 [org.apache.lucene/lucene-queryparser "10.5.1"]
                 [org.clojure/clojure "1.12.6"]
                 [org.clojure/core.cache "1.2.263"]
                 [org.clojure/core.memoize "1.2.281"]
                 [org.clojure/data.csv "1.1.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.clojure/tools.cli "1.4.256"]
                 [org.clojure/tools.logging "1.3.1"]
                 [org.postgresql/postgresql "42.7.13"]
                 [org.webjars.bower/tether "1.4.7"] ; doesn't work with "2.0.0-beta.5", error serving the file
                 [org.webjars.npm/axe-core "4.13.0"]
                 [org.webjars.npm/better-dateinput-polyfill "4.0.0-beta.2"]
                 [org.webjars.npm/popper.js "1.16.1"]
                 [org.webjars/bootstrap "5.3.8"] ; latest before 5.x series
                 [org.webjars/font-awesome "7.3.0"] ; icons don't work with "6.2.0"
                 [org.webjars/jquery "4.0.0"]
                 [prismatic/schema-generators "0.1.5"]
                 [ring-cors "0.1.13"]
                 [ring-middleware-format "0.7.5"]
                 [ring-webjars "0.3.1"]
                 [ring/ring-core "1.15.5"]
                 [ring/ring-defaults "0.7.1"]
                 [ring/ring-devel "1.15.5"]
                 [ring/ring-servlet "1.15.5"]
                 [prismatic/schema "1.4.2"] ;; previsouly, plumatic/schema
                 [metosin/schema-tools "0.14.0"]]

  :min-lein-version "2.9.8"

  :source-paths ["src/clj" "src/cljc"]
  :java-source-paths ["src/java"]
  :test-paths ["src/clj" "src/cljc" "test/clj" "test/cljc"] ; also run tests from src files
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main rems.main
  :migratus {:store :database :db ~(get (System/getenv) "DATABASE_URL" "postgresql://localhost/rems?user=rems")}

  :plugins [[lein-cljfmt "0.6.7"]
            [lein-cprop "1.0.3"]
            [lein-shell "0.5.0"]
            [migratus-lein "0.5.7"]
            [com.github.liquidz/antq "RELEASE"]]

  :antq {}

  :cljfmt {:paths ["project.clj" "src/clj" "src/cljc" "test/clj" "test/cljc"] ; need explicit paths to include cljs
           :remove-consecutive-blank-lines? false} ; too many changes for now, probably not desirable

  :clean-targets ["target"]

  :aliases {"kaocha" ["with-profile" "test" "run" "-m" "kaocha.runner"]
            "alltests" ["do" ["kaocha"]]} ; for lein ancient to work and run all tests

  :profiles
  {:uberjar {:omit-source true
             :prep-tasks [["shell" "sh" "-c" "mkdir -p target/uberjar/resources"]
                          ["shell" "sh" "-c" "git describe --tags --long --always --dirty=-custom > target/uberjar/resources/git-describe.txt"]
                          ["shell" "sh" "-c" "git rev-parse HEAD > target/uberjar/resources/git-revision.txt"]
                          "javac"
                          "compile"]
             :aot :all
             :uberjar-name "rems.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources" "target/uberjar/resources"]}

   :dev [:project/dev :profiles/dev]
   :test [:project/dev :project/test :profiles/test]

   :project/dev {:dependencies [[binaryage/devtools "1.0.7"]
                                [com.clojure-goes-fast/clj-memory-meter "0.5.0"]
                                [criterium "0.4.6"]
                                [lambdaisland/kaocha "1.91.1392"]
                                [lambdaisland/kaocha-junit-xml "1.17.101"]
                                [etaoin "1.1.43"]
                                [ring/ring-mock "0.6.2" :exclusions [cheshire]]
                                [se.haleby/stub-http "0.2.14"]
                                [com.icegreen/greenmail "2.1.13"]
                                [macroz/tangle "0.2.2"]]

                 :plugins [[lein-ancient "0.6.15"]]

                 :jvm-opts ["-Drems.config=dev-config.edn"
                            "-Djdk.attach.allowAttachSelf" ; needed by clj-memory-meter on Java 9+
                            "-XX:-OmitStackTraceInFastThrow"]
                 :source-paths ["env/dev/clj"]
                 :resource-paths ["env/dev/resources"]
                 :repl-options {:init-ns rems
                                :welcome (rems/repl-help)}}
   :project/test {:jvm-opts ["-Drems.config=test-config.edn"]
                  :resource-paths ["env/test/resources"]}
   :profiles/dev {}
   :profiles/test {}})
