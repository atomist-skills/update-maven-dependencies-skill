(ns user
  (:require [atomist.main]
            [atomist.maven]
            [atomist.sdmprojectmodel :as sdm]
            [atomist.cljs-log :as log]
            [cljs.core.async :refer [<! >! timeout chan]]
            [atomist.sha :as sha])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(def token (.. js/process -env -API_KEY_SLIMSLENDERSLACKS_PROD))
(def github-token (.. js/process -env -GITHUB_TOKEN))

(comment
 (atomist.main/handler
  #js {:data {:Push [{:repo {:name "spring-types" :org {:owner "atomisthq" :scmProvider {:credential {:secret github-token}}}}
                      :branch "master"}]}
       :secrets [{:uri "atomist://api-key" :value token}]
       :extensions {:team_id "T095SFFBK"}}
  (fn [& args] (log/info "Response:  " args))))

(comment
 ;; switch a dependency to be managed
 (sdm/do-with-shallow-cloned-project
  (fn [project]
      (go
       (log/info "apply-maven-dependency " (<! (apply-maven-dependency project "com.atomist" "artifact-source" "managed")))
       (log/info (<! (sdm/get-content (<! (promise/from-promise (.getFile ^js project "./pom.xml"))))))
       (log/info (<! (find-declared-dependencies project)))
       :true))
  github-token
  {:repo "spring-types"
   :owner "atomisthq"
   :branch "master"})
 ;; SAMPLE:  compute-maven fingerprint array for the root pom.xml file in a repo
 (sdm/do-with-shallow-cloned-project
  (fn [project]
    (go
     (cljs.pprint/pprint (<! (atomist.main/compute-maven-fingerprints project)))
     :true))
  github-token
  {:repo "spring-types"
   :owner "atomisthq"
   :branch "master"}))