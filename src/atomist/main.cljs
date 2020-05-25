(ns atomist.main
  (:require [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.api :as api]
            [atomist.maven :as maven]
            [atomist.deps :as deps]
            [atomist.config :as config]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- deps->fingerprints [deps]
  (->> (for [gav deps
             :let [data [(gstring/format "%s:%s" (:group gav) (:artifact gav))
                         (if (:version gav)
                           (:version gav)
                           "managed")]]]
         {:type "maven-direct-dep"
          :name (maven/library-name->name (first data))
          :abbreviation "m2"
          :version "0.1.0"
          :data (maven/->coordinate data)
          :path "./pom.xml"
          :sha (maven/data->sha (maven/->coordinate data))
          :displayName (first data)
          :displayValue (:version gav)
          :displayType "MVN Coordinate"})
       (into [])))

(defn extract [request]
  (go (deps->fingerprints (<! (maven/find-declared-dependencies (:project request))))))

(defn ^:export handler
  "handler
    must return a Promise - we don't do anything with the value
    params
      data - Incoming Request #js object
      sendreponse - callback ([obj]) puts an outgoing message on the response topic"
  [data sendreponse]
  (deps/deps-handler
   data
   sendreponse
   :deps-command/show "ShowMavenDependencies"
   :deps-command/sync "SyncMavenDependency"
   :deps-command/update "UpdateMavenDependency"
   :deps/type "maven-direct-dep"
   :deps/apply-library-editor maven/apply-library-editor
   :deps/extract extract
   :deps/->library-version maven/data->library-version
   :deps/->data maven/library-version->data
   :deps/->sha maven/data->sha
   :deps/->name maven/library-name->name
   :deps/validate-policy config/validate-maven-policy
   :deps/validate-command-parameters (api/compose-middleware
                                      [config/set-up-target-configuration]
                                      [config/validate-dependency]
                                      [api/check-required-parameters {:name "dependency"
                                                                      :required true
                                                                      :pattern ".*"
                                                                      :validInput "groupId:artifactId:version"}]
                                      [api/extract-cli-parameters [[nil "--dependency dependency" "group:artifact:version"]]])))
