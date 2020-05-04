(ns atomist.main
  (:require [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.cljs-log :as log]
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

(defn just-fingerprints
  "TODO - we used to support multiple pom.xml files in the Project.  The
          path field was added to the Fingerprint to manage this.
          This version supports only a pom.xml in the root of the Repo.

   Transform Maven dependencies into Fingerprints

   Our data structure has {:keys [group artifact version name version scope]}"
  [request]
  (go
    (try
      (let [deps (<! (maven/find-declared-dependencies (:project request)))]
        (deps->fingerprints deps))
      (catch :default ex
        (log/error "unable to compute maven fingerprints")
        (log/error ex)
        {:error ex
         :message "unable to compute maven fingerprints"}))))

(def apply-policy (partial deps/apply-policy-targets {:type "maven-direct-dep"
                                                      :apply-library-editor maven/apply-library-editor
                                                      :->library-version maven/data->library-version
                                                      :->data maven/library-version->data
                                                      :->sha maven/data->sha
                                                      :->name maven/library-name->name}))

(defn compute-fingerprints
  "TODO - we used to support multiple pom.xml files in the Project.  The
          path field was added to the Fingerprint to manage this.
          This version supports only a pom.xml in the root of the Repo.

   Transform Maven dependencies into Fingerprints

   Our data structure has {:keys [group artifact version name version scope]}"
  [request]
  (go
    (try
      (let [deps (<! (maven/find-declared-dependencies (:project request)))
            fingerprints (deps->fingerprints deps)]
        (log/infof "found %d fingerprints" (count fingerprints))
        (<! (apply-policy
             (assoc request
                    :fingerprints fingerprints)))
        fingerprints)
      (catch :default ex
        (log/error "unable to compute maven fingerprints")
        (log/error ex)
        {:error ex
         :message "unable to compute maven fingerprints"}))))

(defn ^:export handler
  "handler
    must return a Promise - we don't do anything with the value
    params
      data - Incoming Request #js object
      sendreponse - callback ([obj]) puts an outgoing message on the response topic"
  [data sendreponse]
  (deps/deps-handler data
                     sendreponse
                     ["ShowMavenDependencies"]
                     ["SyncMavenDependency"]
                     ["UpdateMavenDependency"
                      (api/compose-middleware
                       [config/set-up-target-configuration]
                       [config/validate-dependency]
                       [api/check-required-parameters {:name "dependency"
                                                       :required true
                                                       :pattern ".*"
                                                       :validInput "groupId:artifactId:version"}]
                       [api/extract-cli-parameters [[nil "--dependency dependency" "group:artifact:version"]]])]
                     just-fingerprints
                     compute-fingerprints
                     config/validate-maven-policy))

(comment
  (enable-console-print!)
  (atomist.main/handler #js {:command "ShowMavenDependencies"
                             :source {:slack {:channel {:id "CDRDCAE2G"}
                                              :user {:id "U2ATJPCSK"}
                                              :team {:id "T29E48P34"}}}
                             :correlation_id "corrid"
                             :api_version "1"
                             :team {:id "T29E48P34"}
                             :configurations [{:name "test"
                                               :enabled true
                                               :parameters [{:name "policy" :value "manualConfiguration"}
                                                            {:name "dependencies" :value "[\"io.spring.javaformat:spring-javaformat-maven-plugin:0.0.7\"]"}]}]
                             :raw_message "mvn fingerprints"
                             :secrets [{:uri "atomist://api-key" :value (.. js/process -env -API_KEY_SLIMSLENDERSLACKS_PROD_GITHUB_AUTH)}]}
                        (fn [& args]
                          (go (cljs.pprint/pprint (first args)))))
  (atomist.main/handler #js {:command "SyncMavenDependency"
                             :source {:slack {:channel {:id "CDRDCAE2G"}
                                              :user {:id "U2ATJPCSK"}
                                              :team {:id "T29E48P34"}}}
                             :correlation_id "corrid"
                             :api_version "1"
                             :team {:id "T29E48P34"}
                             :configurations [{:name "test"
                                               :enabled true
                                               :parameters [{:name "policy" :value "manualConfiguration"}
                                                            {:name "dependencies" :value "[\"io.spring.javaformat:spring-javaformat-maven-plugin:0.0.7\"]"}]}]
                             :raw_message "mvn sync"
                             :secrets [{:uri "atomist://api-key" :value (.. js/process -env -API_KEY_SLIMSLENDERSLACKS_PROD_GITHUB_AUTH)}]}
                        (fn [& args]
                          (go (cljs.pprint/pprint (first args))))))
