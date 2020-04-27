(ns atomist.config
  (:require [atomist.api :as api]
            [cljs.core.async :refer [<! >! timeout chan]]
            [atomist.cljs-log :as log]
            [atomist.deps :as deps]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.json :as json])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn gav->dep [s]
  (let [[_ g a v] (re-find #"(.*):(.*):(.*)" s)]
    [(gstring/format "%s:%s" g a) v]))

(defn- update-dependency
  "switch the GAV string array to be a [[GA version]] string instead"
  [configuration]
  (update configuration :parameters (fn [parameters]
                                      (->> parameters
                                           (map #(if (= "dependencies" (:name %))
                                                   (update % :value (fn [v] (->> (json/->obj v)
                                                                                 (map gav->dep)
                                                                                 (into [])
                                                                                 (pr-str))))
                                                   %))))))

(defn validate-maven-policy
  "validate maven dependency configuration
    all configurations with a policy=manualConfiguration should have a dependency which is a json encoded array of GAVs
    all configurations with other policies use a dependency which is an array of GAs
    as part of validating this

    this is actually validate and fix"
  [handler]
  (fn [request]
    (go
      (try
        (let [configurations (->> (:configurations request)
                                  (map #(if (= "manualConfiguration" (deps/policy-type %))
                                          (update-dependency %)
                                          %))
                                  (map deps/validate-policy))]
          (if (->> configurations
                   (filter :error)
                   (empty?))
            (<! (handler (assoc request :configurations configurations)))
            (<! (api/finish request :failure (->> configurations
                                                  (map :error)
                                                  (interpose ",")
                                                  (apply str))))))
        (catch :default ex
          (log/error ex)
          (<! (api/finish request :failure (-> (ex-data ex) :message))))))))

(defn set-up-target-configuration
  "middleware used to create a policy configuration when running a command handler with a --dependency
   puts it straight into the final form used by apply-policy-targets"
  [handler]
  (fn [request]
    (go
      (log/infof "set up target dependency to converge on %s" (:dependency request))
      (let [[g a v] (re-find #"(.*):(.*):(.*)" (:dependency request))]
        (<! (handler (assoc request
                            :configurations [{:parameters [{:name "policy"
                                                            :value "manualConfiguration"}
                                                           {:name "dependencies"
                                                            :value (gstring/format "[[\"%s:%s\" \"%s\"]]" g a v)}]}])))))))

(defn validate-dependency
  "maven dependency parameters in command handlers should be GAVs in the format groupId:artifactId:version"
  [handler]
  (fn [request]
    (go
      (if-let [dependency (:dependency request)]
        (try
          (let [[_ g a v] (re-find #"(.*):(.*):(.*)" dependency)]
            (if (and g a v)
              (<! (handler request))
              (<! (api/finish request :failure (gstring/format "%s is not a valid maven coordinate" dependency)))))
          (catch :default ex
            (<! (api/finish request :failure (gstring/format "%s is not a valid npm dependency formatted JSON doc" dependency)))))
        (<! (api/finish request :failure "this request requires a dependency to be configured"))))))