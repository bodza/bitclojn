(defproject bitclojn "x.y.z"
    :dependencies [[org.clojure/clojure "1.8.0"]
                   [org.slf4j/slf4j-api "1.7.20"]
                   [org.slf4j/slf4j-jdk14 "1.7.20"]
                   [com.madgag.spongycastle/core "1.52.0.0"]
                   [com.google.guava/guava "22.0-android"]]
    :plugins [[lein-try "0.4.3"]]
;   :global-vars {*warn-on-reflection* true}
    :jvm-opts ["-Xmx12g"]
;   :javac-options ["-g"]
    :source-paths ["src"] :java-source-paths ["src"] :resource-paths ["resources"] :test-paths ["src"]
    :main bitclojn.core
    :aliases {"bitclojn" ["run" "-m" "bitclojn.core"]})
