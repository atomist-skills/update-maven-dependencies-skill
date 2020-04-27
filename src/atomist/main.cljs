(ns atomist.main
  (:require [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.cljs-log :as log]
            [atomist.json :as json]
            [atomist.api :as api]
            [atomist.sha :as sha]
            [atomist.maven :as maven]
            [atomist.deps :as deps]
            [atomist.config :as config]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; name groupId:artifactId
;; type maven-direct-dep
;; data {:keys [group artifact name version scope]}
;; sha of "groupId:artifactId:version"

(defn- deps->fingerprints [deps]
  (->> (for [gav deps
             :let [data [(gstring/format "%s:%s" (:group gav) (:artifact gav))
                         (if (:version gav)
                           (:version gav)
                           "managed")]]]
         {:type "maven-direct-dep"
          :name (gstring/format "%s:%s" (:group gav) (:artifact gav))
          :abbreviation "mvn"
          :version "0.1.0"
          :data data
          :path "./pom.xml"
          :sha (sha/sha-256 (json/->str data))
          :displayName (gstring/format "%s:%s" (:group gav) (:artifact gav))
          :displayValue (:version gav)
          :displayType "Maven declared dependencies"})
       (into [])))

(defn just-fingerprints
  "TODO - we used to support multiple pom.xml files in the Project.  The
          path field was added to the Fingerprint to manage this.
          This version supports only a pom.xml in the root of the Repo.

   Transform Maven dependencies into Fingerprints

   Our data structure has {:keys [group artifact version name version scope]}"
  [request project]
  (go
    (try
      (let [deps (<! (maven/find-declared-dependencies project))]
        (deps->fingerprints deps))
      (catch :default ex
        (log/error "unable to compute maven fingerprints")
        (log/error ex)
        {:error ex
         :message "unable to compute maven fingerprints"}))))

(defn compute-fingerprints
  "TODO - we used to support multiple pom.xml files in the Project.  The
          path field was added to the Fingerprint to manage this.
          This version supports only a pom.xml in the root of the Repo.

   Transform Maven dependencies into Fingerprints

   Our data structure has {:keys [group artifact version name version scope]}"
  [request project]
  (go
    (try
      (let [deps (<! (maven/find-declared-dependencies project))
            fingerprints (deps->fingerprints deps)]
        (log/infof "found %d fingerprints" (count fingerprints))
        (<! (deps/apply-policy-targets
             (assoc request :project project :fingerprints fingerprints)
             "maven-direct-dep"
             maven/apply-library-editor))
        fingerprints)
      (catch :default ex
        (log/error "unable to compute maven fingerprints")
        (log/error ex)
        {:error ex
         :message "unable to compute maven fingerprints"}))))

(defn set-up-target-configuration
  "middleware used to create a policy configuration when running a command handler with a --dependency
   puts it straight into the final form used by apply-policy-targets"
  [handler]
  (fn [request]
    (log/infof "set up target dependency to converge on %s" (:dependency request))
    (let [[g a v] (re-find #"(.*):(.*):(.*)" (:dependency request))]
      (handler (assoc request
                      :configurations [{:parameters [{:name "policy"
                                                      :value "manualConfiguration"}
                                                     {:name "dependencies"
                                                      :value (gstring/format "[[\"%s:%s\" \"%s\"]]" g a v)}]}])))))

(defn ^:export handler
  "handler
    must return a Promise - we don't do anything with the value
    params
      data - Incoming Request #js object
      sendreponse - callback ([obj]) puts an outgoing message on the response topic"
  [data sendreponse]
  (deps/deps-handler data sendreponse
                     ["ShowMavenDependencies" just-fingerprints]
                     ["SyncMavenDependency"]
                     ["UpdateMavenDependency" compute-fingerprints
                      (api/compose-middleware
                       [set-up-target-configuration]
                       [config/validate-dependency]
                       [api/check-required-parameters {:name "dependency"
                                                       :required true
                                                       :pattern ".*"
                                                       :validInput "groupId:artifactId:version"}]
                       [api/extract-cli-parameters [[nil "--dependency dependency" "group:artifact:version"]]])]
                     config/validate-maven-policy))
