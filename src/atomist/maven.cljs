(ns atomist.maven
  (:require [atomist.cljs-log :as log]
            [atomist.promise :as promise]
            [atomist.api :as api]
            [cljs.core.async :refer [<! >! timeout chan]]
            [atomist.sdmprojectmodel :as sdm]
            [clojure.string :as s]
            [goog.string :as gstring]
            [goog.string.format]
            ["@atomist/sdm-pack-spring/lib/maven/parse/fromPom" :as fromPom]
            ["@atomist/automation-client" :as automation-client]
            ["@atomist/sdm-pack-spring/lib/xml/XmldocFileParser" :as xml]
            [atomist.json :as json]
            [cljs-node-io.core :as io])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ast-utils (. automation-client -astUtils))
(def xml-doc-file-parser (. xml -XmldocFileParser))

(defn validate-maven-coordinate [handler]
  (fn [request]
    (if-let [dependency (:dependency request)]
      (let [[_ group artifact version] (re-find #"(.*):(.*):(.*)" dependency)]
        (if (and group artifact version)
          (handler request)
          (api/finish request :failure (gstring/format "%s is not a valid maven coordinate" dependency))))
      (api/finish request :failure "this request requires a dependency to be configured"))))

(defn validate-policy [handler]
  (fn [request]
    (handler request)))

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
  [project pr-opts library-name library-version]
  (let [[_ group artifact] (re-find #"(.*):(.*)" library-name)]
    ((sdm/commit-then-PR
      (fn [p] (go
               (try
                 (<! (apply-maven-dependency project [{:data (json/->str {:group group :artifact artifact :version library-version})}]))
                 :success
                 (catch :default ex
                   (log/error "failure updating project.clj for dependency change" ex)
                   :failure))))
      pr-opts) project)))