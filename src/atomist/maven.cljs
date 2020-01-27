(ns atomist.maven
  (:require [atomist.cljs-log :as log]
            [atomist.promise :as promise]
            [cljs.core.async :refer [<! >! timeout chan]]
            ["@atomist/sdm-pack-spring/lib/maven/parse/fromPom" :as fromPom])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- find-declared-dependencies
  "
   returns array of {:keys [group artifact name version scope]}"
  [project]
  (log/info "find declared pom.xml deps at " (. project -baseDir))
  (let [c (chan)
        p (.findDeclaredDependencies fromPom project)]
    (.catch
     (.then p (fn [v] (go (>! c (:dependencies (js->clj v :keywordize-keys true))))))
     (fn [error]
       (log/info "error running findDeclaredDependencies" error)
       (go (>! c []))))
    c))
