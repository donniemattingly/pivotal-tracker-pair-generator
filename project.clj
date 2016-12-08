(defproject pair-generator "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [ring/ring-core "1.6.0-beta6"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-devel "1.6.0-beta6"]
                 [http-kit "2.1.16"]
                 [clj-http "2.3.0"]
                 [cheshire "5.6.3"]
                 [environ "1.1.0"]]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler pair-generator.handler/app}
  :target-path "target/%s"
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})
