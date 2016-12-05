(ns matchmaker.core
  (:require [compojure.core :refer :all]
            [ring.middleware.defaults :refer :all]
            [org.httpkit.server :refer [run-server]]
            [clj-http.client :as client]
            [clojure.pprint :as pprint]
            [cheshire.core :refer :all]
            [clojure.walk :as walk]))                        ; httpkit is a server

(defroutes myapp
  (GET "/" []
       {:status 200 :body "Hello World"})
  (GET "/is-403" []
       {:status 403 :body ""})
  (GET "/is-json" []
       {:status 200 :headers {"Content-Type" "application/json"} :body "{}"}))

(defn -main []
  (run-server (wrap-defaults myapp site-defaults) {:port 5000}))

(def project-id "1900397")

(defn get-project-members
  [token project-id]
  (walk/keywordize-keys (map  (fn [member]
                                (select-keys (get member "person") ["name" "id" "initials"])) 
                              (parse-string
                               (:body (client/get
                                       (str "https://www.pivotaltracker.com/services/v5/projects/" project-id "/memberships")
                                       {:headers {"X-TrackerToken" token}}))))))

(defn get-active-stories
  [token project-id]
  (map walk/keywordize-keys (parse-string
                             (:body (client/get
                                     (str "https://www.pivotaltracker.com/services/v5/projects/" project-id "/stories?filter=state:started")
                                     {:headers {"X-TrackerToken" token}})))))

(defn filter-stories-with-label
  [stories label]
  (filter (fn [story]
            (not (some #(= label %) (map :name (:labels story)))))
          stories))

(defn get-story-owners
  [story project-members]
  (filter (fn [member]
            (some #(= (:id member) %) (:owner_ids story)))
          project-members))

(defn get-active-members
  ([token project-id] (get-active-members token project-id (get-active-stories token project-id)))
  ([token project-id active-stories]
   (let [project-members (get-project-members token project-id)]
     (filter (fn [member]
               (contains? (set (flatten (map :owner_ids active-stories))) (:id member))) project-members))))

(defn get-members-for-story
  [project-members story]
  (map (fn [id]
         (first (filter (comp #{id} :id)
                        project-members))) (:owner_ids story)))

(defn get-current-pairs
  [active-stories project-members]
  (map (partial get-members-for-story project-members) active-stories))

(defn get-new-pairs
  "Takes a list of currently being worked stories and a list of project
  members who should be paired. Returns a set of pairs (possibly leaving
  one person alone) where no person is paired with the last person they 
  paired with"
  [active-stories pairable-members]
  (let [current-pairs (get-current-pairs active-stories pairable-members)]
    (let [orphaned-pairs (set (map rand-nth current-pairs))]
         orphaned-pairs)))
