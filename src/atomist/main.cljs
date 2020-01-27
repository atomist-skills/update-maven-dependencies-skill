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
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn compute-maven-fingerprints
  "TODO - we used to support multiple pom.xml files in the Project.  The
          path field was added to the Fingerprint to manage this.
          This version supports only a pom.xml in the root of the Repo.

   Transform Maven dependencies into Fingerprints"
  [project]
  (go
   (let [deps (<! (maven/find-declared-dependencies project))]
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
          (into [])))))

(defn process-request
  "process the request pipeline for any events arriving in this skill"
  [request]
  (let [done-channel (chan)]
    ;; create a pipeline of handlers but always end by writing to the done channel and logging something
    ((-> (fn [ch-request]
           (log/info "----> finished")
           (go (>! (:done-channel ch-request) :done)))
         (api/send-fingerprints)
         (api/run-sdm-project-callback (fn [project] (go (<! (compute-maven-fingerprints project)))))
         (api/extract-github-token)
         (api/create-ref-from-push-event)) (assoc request
                                             :done-channel done-channel))
    done-channel))

(defn ^:export handler
  "handler
    must return a Promise - we don't do anything with the value
    params
      data - Incoming Request #js object
      sendreponse - callback ([obj]) puts an outgoing message on the response topic"
  [data sendreponse]
  (promise/chan->promise
   (process-request (assoc (js->clj data :keywordize-keys true)
                      :sendreponse sendreponse))))
