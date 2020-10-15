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

(ns atomist.maven
  (:require [atomist.cljs-log :as log]
            [atomist.promise :as promise]
            [atomist.api :as api]
            [cljs.core.async :refer [<! >! timeout chan]]
            [clojure.string :as s]
            [goog.string :as gstring]
            [goog.string.format]
            ["@atomist/sdm-pack-spring/lib/maven/parse/fromPom" :as fromPom]
            ["@atomist/automation-client" :as automation-client]
            ["@atomist/sdm-pack-spring/lib/xml/XmldocFileParser" :as xml]
            [atomist.json :as json]
            [cljs-node-io.core :as io]
            [atomist.deps :as deps]
            [atomist.sha :as sha])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn library-name->name [s]
  (-> s
      (s/replace-all #"@" "")
      (s/replace-all #"/" "::")))

(defn data->sha [data]
  (sha/sha-256 (json/->str data)))

(defn data->library-version [data]
  [(if (= (:group data) (:artifact data))
     (:artifact data)
     (gstring/format "%s:%s" (:group data) (:artifact data)))
   (:version data)])

(defn library-version->data [[library version]]
  (assoc
   (if-let [[_ group artifact] (re-find #"(.*):(.*)" library)]
     {:group group
      :artifact artifact}
     {:group library
      :artifact library})
   :version version))

(defn ->coordinate [[n v]]
  (merge
   {:version v}
   (if-let [[_ g a] (re-find #"(.*):(.*)" n)]
     {:group g
      :artifact a}
     {:group n
      :artifact n})))

(def ast-utils (. automation-client -astUtils))
(def xml-doc-file-parser (. xml -XmldocFileParser))

(defn find-declared-dependencies
  "Use sdm-pack-spring to extract dependency gavs from a pom.xml in the root of this project.
   findDeclaredDependencies takes a second argument with the glob pattern for pom.xml but the
   default version searches only in the root of the Project.

   params
     project - the SDM project

   returns chan
     that will emit an array of {:keys [group artifact name version scope]}"
  [project]
  (log/info "find declared pom.xml deps at " (. project -baseDir))
  (promise/from-promise
   (.findDeclaredDependencies fromPom project)
   (fn [value] (:dependencies (js->clj value :keywordize-keys true)))
   (fn [error] (log/warn "error running findDeclaredDependencies:  " error) [])))

(defn- indentation-of
  "find the level of indentation for a string somewhere in the xml content"
  [content what]
  (let [line (->> (s/split content "\n")
                  (filter #(s/includes? % what))
                  first)]
    (if line
      (subs 0 (s/index-of line what))
      "")))

(defn- update-version-element!
  "This is here just for it's side-effect on the mutable maven dependency xml Node
    params
      dep - effectively mutable reference to the dependency element in a pom.xml
      version - version string that

    returns nothing - alters the dep reference directly"
  [dep version]
  (let [version-match #"<version>.*</version>"
        new-version (gstring/format "<version>%s</version>" version)]
    (cond
      ;; removed if managed
      (and (= version "managed") (s/includes? (.-$value dep) "<version>"))
      (set! (.-$value dep) (s/replace (.-$value dep) version-match ""))

      ;; update old version
      (and version (re-find version-match (.-$value dep)))
      (set! (.-$value dep) (s/replace (.-$value dep) version-match new-version))

      ;; add version if missing
      version
      (set! (.-$value dep) (s/replace
                            (.-$value dep)
                            #"</artifactId>"
                            (gstring/format "</artifactId>\n%s%s" (indentation-of (.-$value dep) "<artifactId>") new-version))))))

(defn apply-maven-dependency
  "matches dependency xml nodes for a particular group/artifact

   params
     off-targets - coll of {:keys [data type name]}

   returns chan that will emit the Project when the edit is complete"
  [project off-targets]
  (go
    (<! (promise/from-promise
         (.doWithAllMatches
          ast-utils
          project
          (xml-doc-file-parser.)
          "pom.xml"
          "//project/dependencies/dependency[/artifactId][/groupId]"
          (fn [dep]
            (log/infof "check %s" (.-$value dep))
            (doseq [{:keys [data]} off-targets
                    :let [{:keys [group artifact version]} (json/->obj data)]]
              (log/infof "check %s/%s" group artifact)
              (let [group-id (.find (. dep -$children)
                                    (fn [c] (s/starts-with? (. c -$value) "<groupId>")))
                    artifact-id (.find (. dep -$children)
                                       (fn [c] (s/starts-with? (. c -$value) "<artifactId>")))]
                (if (and
                     (s/includes? (. group-id -$value) (str ">" group "<"))
                     (s/includes? (. artifact-id -$value) (str ">" artifact "<")))
                  (update-version-element! dep version))))))))
    project))

(defn apply-library-editor
  [project target-fingerprint]
  (go
    (try
      (<! (apply-maven-dependency project [{:data (json/->str (:data target-fingerprint))}]))
      :success
      (catch :default ex
        (log/error "failure updating pom.xml" ex)
        :failure))))
