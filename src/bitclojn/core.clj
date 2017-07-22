(ns bitclojn.core)

(defmacro § [& _])

(defmacro def- [s i] `(def ~(vary-meta s assoc :private true) ~i))

(defmacro when' [y & w]
    (let [[_ & w] (if (= '=> (first w)) (rest w) (cons nil w))]
        `(if ~y (do ~@w) ~_)))
(defmacro let-when [x y & w]
    (let [[_ & w] (if (= '=> (first w)) (rest w) (cons nil w))]
        `(let [~@x] (if ~y (do ~@w) ~_))))

(defn -main [& args]
    )
