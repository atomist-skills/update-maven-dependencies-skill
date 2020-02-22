(ns atomist.main
  (:require [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [clojure.string :as s]
            [goog.crypt.base64 :as b64]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.cljs-log :as log]
            [atomist.editors :as editors]
            [atomist.sdmprojectmodel :as sdm]
            [atomist.json :as json]
            [atomist.api :as api]
            [atomist.sha :as sha]
            [atomist.promise :as promise]
            [atomist.maven :as maven]
            [atomist.deps :as deps]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- deps->fingerprints [deps]
  (->> (for [gav deps
             :let [data (assoc gav :version (if (:version gav)
                                              (:version gav)
                                              "managed"))]]
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

(defn set-up-target-configuration [handler]
  (fn [request]
    (log/infof "set up target dependency to converge on [%s]" (:dependency request))
    (handler (assoc request
               :configurations [{:parameters [{:name "policy"
                                               :value "manualConfiguration"}
                                              {:name "dependencies"
                                               :value (gstring/format "[%s]" (:dependency request))}]}]))))

(defn check-for-targets-to-apply [handler]
  (fn [request]
    (if (not (empty? (-> request :data :CommitFingerprintImpact :offTarget)))
      (handler request)
      (go (>! (:done-channel request) :done)))))

(defn- handle-push-event [request]
  ((-> (api/finished :message "handling Push" :success "Successfully handled Push event")
       (api/send-fingerprints)
       (maven/validate-policy)
       (api/run-sdm-project-callback compute-fingerprints)
       (api/extract-github-token)
       (api/create-ref-from-push-event)) request))

(defn- handle-impact-event [request]
  ((-> (api/finished :message "handling CommitFingerprintImpact" :success "Successfully handled CommitFingerprintImpact event")
       (api/extract-github-token)
       (api/create-ref-from-repo
        (-> request :data :CommitFingerprintImpact :repo)
        (-> request :data :CommitFingerprintImpact :branch))
       (check-for-targets-to-apply)) request))

(defn update-command-handler [request]
  ((-> (api/finished :message "handling application CommandHandler")
       (api/show-results-in-slack :result-type "fingerprints")
       (api/run-sdm-project-callback compute-fingerprints)
       (set-up-target-configuration)
       (api/create-ref-from-first-linked-repo)
       (api/extract-linked-repos)
       (api/extract-github-user-token)
       (maven/validate-maven-coordinate)
       (api/check-required-parameters {:name "dependency"
                                       :required true
                                       :pattern ".*"
                                       :validInput "group:artifact:version"})
       (api/extract-cli-parameters [[nil "--dependency dependency" "group:artifact:version"]])
       (api/set-message-id)) (assoc request :branch "master")))

(defn command-handler [request]
  ((-> (api/finished :message "handling CommandHandler" :success "Command Handler invoked")
       (api/show-results-in-slack :result-type "fingerprints")
       (api/run-sdm-project-callback just-fingerprints)
       (api/create-ref-from-first-linked-repo)
       (api/extract-linked-repos)
       (api/extract-github-user-token)
       (api/set-message-id)) (assoc request :branch "master")))

(defn ^:export handler
  "handler
    must return a Promise - we don't do anything with the value
    params
      data - Incoming Request #js object
      sendreponse - callback ([obj]) puts an outgoing message on the response topic"
  [data sendreponse]
  (api/make-request
   data
   sendreponse
   (fn [request]
     (cond

       ;; handle Push events
       (contains? (:data request) :Push)
       (handle-push-event request)

       ;; handle Commit Fingeprint Impact events
       (= :CommitFingerprintImpact (:data request))
       (handle-impact-event request)

       ;;
       (= "ShowMavenDependencies" (:command request))
       (command-handler request)

       (= "UpdateMavenDependency" (:command request))
       (update-command-handler request)

       :else
       (api/finish request :failure "did not recognize event")))))
