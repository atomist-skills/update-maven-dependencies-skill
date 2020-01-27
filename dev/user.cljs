(ns user
  (:require [atomist.main]
            [atomist.cljs-log :as log]
            [atomist.sha :as sha]))

(enable-console-print!)

(def token (.. js/process -env -API_KEY_SLIMSLENDERSLACKS_PROD))
(def github-token (.. js/process -env -GITHUB_TOKEN))

(comment
 (atomist.main/handler
  #js {:data {:Push [{:repo {:name "spring-types" :org {:owner "atomisthq" :scmProvider {:credential {:secret github-token}}}}
                      :branch "master"}]}
       :secrets [{:uri "atomist://api-key" :value token}]
       :extensions {:team_id "T095SFFBK"}}
  (fn [& args] (log/info "Response:  " args))))