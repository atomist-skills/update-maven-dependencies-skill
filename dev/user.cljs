(ns user
  (:require [atomist.main]
            [atomist.maven]
            [atomist.sdmprojectmodel :as sdm]
            [atomist.cljs-log :as log]
            [cljs.core.async :refer [<! >! timeout chan]]
            [atomist.local-runner :refer [call-event-handler fake-push fake-command-handler]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(comment
  (-> (fake-push "T095SFFBK" "atomisthq" "spring-types" "master")
      (assoc :configurations [{:parameters [{:name "policy" :value "manualConfiguration"}
                                            {:name "dependencies" :value "[\"groupId:artifactId:version\"]"}]}])
      (call-event-handler atomist.main/handler))

  (-> (fake-command-handler "T29E48P34" "ShowMavenDependencies" "mvn fingerprints" "CDRDCAE2G" "U2ATJPCSK")
      (assoc :configurations [{:name "test"
                               :enabled true
                               :parameters [{:name "policy" :value "manualConfiguration"}
                                            {:name "dependencies" :value "[\"io.spring.javaformat:spring-javaformat-maven-plugin:0.0.7\"]"}]}])
      (call-event-handler atomist.main/handler))

  (-> (fake-command-handler "T29E48P34" "SyncMavenDependency" "mvn fingerprints" "CDRDCAE2G" "U2ATJPCSK")
      (assoc :configurations [{:name "test"
                               :enabled true
                               :parameters [{:name "policy" :value "manualConfiguration"}
                                            {:name "dependencies" :value "[\"io.spring.javaformat:spring-javaformat-maven-plugin:0.0.7\"]"}]}])
      (call-event-handler atomist.main/handler)))
