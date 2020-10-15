;; Copyright © 2020 Atomist, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns user
  (:require [atomist.main]
            [atomist.maven]
            [atomist.sdmprojectmodel :as sdm]
            [atomist.cljs-log :as log]
            [cljs.core.async :refer [<! >! timeout chan]]
            [atomist.local-runner :refer [call-event-handler fake-push fake-command-handler]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(comment

  (enable-console-print!)

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
