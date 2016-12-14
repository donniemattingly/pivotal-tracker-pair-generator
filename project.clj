(defproject pair-generator "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :source-paths ["src/clj"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [ring/ring-core "1.6.0-beta6"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-devel "1.6.0-beta6"]
                 [ring/ring-json "0.4.0"]
                 [http-kit "2.1.16"]
                 [clj-http "2.3.0"]
                 [cheshire "5.6.3"]
                 [environ "1.1.0"]
                 ;; Clojurescript
                 [org.clojure/clojurescript "1.7.122"]
                 [cljs-ajax "0.5.1"]
                 [prismatic/dommy "1.1.0"]
                 ]
  :plugins [[lein-ring "0.9.7"]
            [lein-environ "1.1.0"]
            [lein-figwheel "0.4.1"]]
  :ring {:handler pair-generator.handler/app}
  :target-path "target/%s"
  :cljsbuild {
              :builds [ { :id "pair-generator"
                         :source-paths ["src/cljs"]
                         :figwheel true
                         :compiler {:main "pair-generator.app"
                                    :asset-path "js/out"
                                    :output-to "resources/public/js/app.js"
                                    :output-dir "resources/public/js/out"} } ]
              }
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})
