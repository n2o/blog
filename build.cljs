(ns build
  (:require ["@markdoc/markdoc$default" :as markdoc]
            ["zx" :refer [glob]]
            [nbb.core :refer [slurp await]]
            [promesa.core :as p]))

(defn markdown-to-html [markdown]
  (-> markdown
      markdoc/parse
      markdoc/transform
      markdoc/renderers.html))

(defn process-post-path [post-path]
  (p/-> (slurp post-path)
        markdown-to-html))

(defn build []
  (p/let [posts (glob "posts/**/*.md")
          posts (js->clj posts)
          posts (p/all (map process-post-path posts))]
    posts))

(comment
  (await (build))
  nil)
