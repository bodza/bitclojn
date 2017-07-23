(defproject bitclojn "x.y.z"
    :dependencies [[org.clojure/clojure "1.8.0"]
                   [org.slf4j/slf4j-api "1.7.20"]
                   [org.slf4j/slf4j-jdk14 "1.7.20"]
                   [com.h2database/h2 "1.3.167"]
                   [com.madgag.spongycastle/core "1.52.0.0"]
                   [com.google.protobuf/protobuf-java "2.6.1"]
                   [com.google.guava/guava "22.0-android"]
                   [net.jcip/jcip-annotations "1.0"]
                   [com.lambdaworks/scrypt "1.4.0"]
                   [postgresql/postgresql "9.1-901.jdbc4"]
                   [com.squareup.okhttp3/okhttp "3.6.0"]]
    :plugins [[lein-try "0.4.3"]]
;   :global-vars {*warn-on-reflection* true}
    :jvm-opts ["-Xmx12g"]
;   :javac-options ["-g"]
    :source-paths ["src"] :java-source-paths ["src"] :resource-paths ["resources"] :test-paths ["src"]
    :main bitclojn.core
    :aliases {"bitclojn" ["run" "-m" "bitclojn.core"]})
