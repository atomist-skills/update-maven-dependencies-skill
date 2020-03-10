(ns atomist.config
  (:require [atomist.api :as api]
            [atomist.cljs-log :as log]
            [atomist.deps :as deps]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.json :as json]))

(defn validate-dependency
  "maven dependency parameters in command handlers should be GAVs in the format groupId:artifactId:version"
  [handler]
  (fn [request]
    (if-let [dependency (:dependency request)]
      (try
        (let [[_ g a v] (re-find #"(.*):(.*):(.*)" dependency)]
          (if (and g a v)
            (handler request)
            (api/finish request :failure (gstring/format "%s is not a valid maven coordinate" dependency))))
        (catch :default ex
          (api/finish request :failure (gstring/format "%s is not a valid npm dependency formatted JSON doc" dependency))))
      (api/finish request :failure "this request requires a dependency to be configured"))))

(defn gav->dep [s]
  (let [[_ g a v] (re-find #"(.*):(.*):(.*)" s)]
    [(gstring/format "%s:%s" g a) v]))

(defn update-dependency
  "switch the GAV string array to be a [[GA version]] string instead"
  [configuration]
  (update configuration :parameters (fn [parameters]
                                      (->> parameters
                                           (map #(if (= "dependencies" (:name %))
                                                   (update % :value (fn [v] (->> (json/->obj v)
                                                                                 (gav->dep)
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
    (try
      (let [configurations (->> (:configurations request)
                                (map #(if (= "manualConfiguration" (deps/policy-type %))
                                        (update-dependency %)
                                        %))
                                (map deps/validate-policy))]
        (if (->> configurations
                 (filter :error)
                 (empty?))
          (handler (assoc request :configurations configurations))
          (api/finish request :failure (->> configurations
                                            (map :error)
                                            (interpose ",")
                                            (apply str)))))
      (catch :default ex
        (log/error ex)
        (api/finish request :failure (-> (ex-data ex) :message))))))