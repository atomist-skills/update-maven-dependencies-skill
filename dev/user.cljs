(ns user
  (:require [atomist.main]
            [atomist.maven]
            [atomist.sdmprojectmodel :as sdm]
            [atomist.cljs-log :as log]
            [cljs.core.async :refer [<! >! timeout chan]]
            [atomist.sha :as sha])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)
(sdm/enable-sdm-debug-logging)

(def token (.. js/process -env -API_KEY_SLIMSLENDERSLACKS_PROD))
(def github-token (.. js/process -env -GITHUB_TOKEN))

(comment
  (.catch
   (.then
    (atomist.main/handler
     #js {:data {:Push [{:branch "master"
                         :repo {:name "spring-types"
                                :org {:owner "atomisthq"
                                      :scmProvider {:providerId "zjlmxjzwhurspem"
                                                    :credential {:secret github-token}}}}
                         :after {:message ""}}]}
          :configurations [{:parameters [{:name "policy" :value "manualConfiguration"}
                                         {:name "dependencies" :value "[\"groupId:artifactId:version\"]"}]}]
          :secrets [{:uri "atomist://api-key" :value token}]
          :extensions {:team_id "T095SFFBK"}}
     (fn [& args] (log/info "Response:  " args)))
    (fn [v] "value " (println v)))
   (fn [error] "error " (println error))))

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
       (cljs.pprint/pprint (<! (atomist.main/just-fingerprints {} project)))
       :true))
   github-token
   {:repo "spring-types"
    :owner "atomisthq"
    :branch "master"}))