(ns pair-generator.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [org.httpkit.server :refer [run-server]]
            [clj-http.client :as client]
            [clojure.pprint :as pprint]
            [cheshire.core :refer :all]
            [clojure.walk :as walk]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.util.response :refer [resource-response response]]
            [ring.middleware.json :as middleware]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [environ.core :refer [env]]))

(def token (env :tracker-token))
(def project-id (env :tracker-project-id))

(defn get-project-members
  [token project-id]
  (walk/keywordize-keys (map  (fn [member]
                                (select-keys (get member "person") ["name" "id" "initials"])) 
                              (parse-string
                               (:body (client/get
                                       (str "https://www.pivotaltracker.com/services/v5/projects/"
                                            project-id 
                                            "/memberships")
                                       {:headers {"X-TrackerToken" token}
                                        :insecure true}))))))

(defn get-active-stories
  ([] (get-active-stories token project-id))
  ([token project-id]
   (map walk/keywordize-keys (parse-string
                              (:body (client/get
                                      (str "https://www.pivotaltracker.com/services/v5/projects/" 
                                           project-id 
                                           "/stories?filter=state:started")
                                      {:headers {"X-TrackerToken" token}
                                       :insecure true}))))))

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
  ([] (get-active-members token project-id))
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
  (set (map (partial get-members-for-story project-members) active-stories)))


(defn get-new-pairs
  "Takes a list of currently being worked stories and a list of project
  members who should be paired. Returns a set of pairs (possibly leaving
  one person alone) where no person is paired with the last person they 
  paired with"
  [active-stories pairable-members]
  (let [current-pairs (get-current-pairs active-stories pairable-members)]
    (let [pair-set (set pairable-members)]
      (loop [anchors (set (take (/ (count pair-set) 2) (shuffle pair-set)))
            orphans (set (remove anchors pair-set))
             pairs []]
        (if (every? empty? [anchors orphans])
          pairs
          (let [new-pair [(first anchors) (first orphans)]]
            (if (current-pairs new-pair)
              (if (or (= 1 (count anchors)) (= 1 (count orphans)))
                (conj pairs new-pair)
                (recur (shuffle anchors) (shuffle orphans) pairs))
              (recur (rest anchors) (rest orphans) (conj pairs new-pair)))))))))



(defroutes app-routes
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  
  (route/resources "/")

  (GET "/env" []
       (generate-string {:token (env :tracker-token) :project-id (env :tracker-project-id)}))
  (GET "/members" [] 
       (response (get-project-members token project-id)))
  (GET "/members/active" [] 
       (response (generate-string (get-active-members token project-id))))
  (GET "/stories/active" []
       (response (get-active-stories token project-id)))
  (GET "/pairs/current" []
       (response (generate-string (get-current-pairs 
                                  (get-active-stories)
                                  (get-active-members)))))
  (GET "/pairs/new" []
       (response (generate-string (get-new-pairs (get-active-stories) (get-active-members)))))

  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-reload)
      (wrap-keyword-params)
      (middleware/wrap-json-body)
      (middleware/wrap-json-response)
      (wrap-defaults api-defaults)))