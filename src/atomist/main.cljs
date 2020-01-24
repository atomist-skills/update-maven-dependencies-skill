(ns atomist.main
  (:require [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<! timeout chan]]
            [clojure.string :as s]
            [goog.crypt.base64 :as b64]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.cljs-log :as log]
            [atomist.editors :as editors]
            [atomist.sdmprojectmodel :as sdm]
            [atomist.json :as json]
            [atomist.api :as api]
            [atomist.promise :as promise])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn compute-fingerprints [project]
  (log/info "compute fingerprints in " (.keys js/Object project))
  [])

(defn process-request
  "process the request pipeline for any events arriving in this skill"
  [request]
  (let [done-channel (chan)]
    ;; create a pipeline of handlers but always end by writing to the done channel and logging something
    ((-> (fn [ch-request]
           (log/info "----> finished")
           (go (>! (:done-channel ch-request) :done)))
         (api/send-fingerprints)
         (api/run-sdm-project-callback (fn [project] (compute-fingerprints project)))
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
