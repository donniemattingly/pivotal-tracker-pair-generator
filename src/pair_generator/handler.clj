(ns pair-generator.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [org.httpkit.server :refer [run-server]]
            [clj-http.client :as client]
            [clojure.pprint :as pprint]
            [cheshire.core :refer :all]
            [clojure.walk :as walk]
            [ring.middleware.reload :refer [wrap-reload]]
            [environ.core :refer [env]]))


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
  [token project-id]
  (map walk/keywordize-keys (parse-string
                             (:body (client/get
                                     (str "https://www.pivotaltracker.com/services/v5/projects/" 
                                          project-id 
                                          "/stories?filter=state:started")
                                     {:headers {"X-TrackerToken" token}
                                      :insecure true})))))

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
              (recur (shuffle anchors) (shuffle orphans) pairs)
              (recur (rest anchors) (rest orphans) (conj pairs new-pair)))))))))

(def active-stories (get-active-stories token project-id))
(def active-members (get-active-members token project-id))


(defroutes app-routes
  (GET "/" [] "Hello World")
  (GET "/members" [] 
       {:status 200 :headers {"Content-Type" "application/json"} 
        :body (generate-string (get-project-members token project-id))})
  (GET "/members/active" [] 
       {:status 200 :headers {"Content-Type" "application/json"} 
        :body (generate-string (get-active-members token project-id))})
  (GET "/stories/active" []
       {:status 200 :headers {"Content-Type" "application/json"}
        :body (generate-string (get-active-stories token project-id))})
  (GET "/pairs/current" []
       {:status 200 :headers {"Content-Type" "application/json"}
        :body (generate-string (get-current-pairs 
                                (get-active-stories token project-id)
                                (get-active-members token project-id)))})
  (GET "/pairs/new" []
       {:status 200 :headers {"Content-Type" "application/json"}
        :body (generate-string (get-new-pairs active-stories active-members))})
  (route/not-found "Not Found"))

(def app
  (wrap-reload (wrap-defaults app-routes site-defaults)))
