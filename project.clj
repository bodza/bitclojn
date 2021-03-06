(defproject bitclojn "x.y.z"
    :dependencies [[org.clojure/clojure "1.8.0"]
                   [org.clojure/core.rrb-vector "0.0.11"]
                   [org.clojure/data.priority-map "0.0.7"]
                   [org.clojure/tools.logging "0.4.0"]
                   [org.flatland/ordered "1.5.6"]
                   [slingshot "0.12.2"]
                   [com.madgag.spongycastle/core "1.52.0.0"]
                   [com.google.guava/guava "22.0-android"]]
    :plugins [[lein-try "0.4.3"]]
;   :global-vars {*warn-on-reflection* true}
    :jvm-opts ["-Xmx12g"]
;   :javac-options ["-g"]
    :source-paths ["src"] :java-source-paths ["src"] :resource-paths ["resources"] :test-paths ["src"]
    :main bitclojn.core
    :aliases {"bitclojn" ["run" "-m" "bitclojn.core"]})
