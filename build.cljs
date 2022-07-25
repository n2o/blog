(ns build
  "Blogengine, based on https://www.alexandercarls.de/markdoc-nbb-clojure/"
  (:require ["@markdoc/markdoc$default" :as markdoc]
            ["path" :as path]
            ["zx" :refer [glob fs]]
            [applied-science.js-interop :as j]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [nbb.core :refer [slurp await]]
            [promesa.core :as p]))

(def dist-folder "dist")
(def template (fs.readFileSync "template.html" "utf8"))

(defn date->human [date]
  (.toLocaleDateString date "en-US" #js {:year "numeric" :month "long" :day "numeric"}))
(def md-to-human-date {:transform (fn [parameters] (date->human (j/get parameters 0)))})

(defn parse-frontmatter [ast]
  (when-let [frontmatter (j/get-in ast [:attributes :frontmatter])]
    (edn/read-string frontmatter)))

(defn markdown-to-html [markdown]
  (let [ast (markdoc/parse markdown)
        frontmatter (parse-frontmatter ast)
        rendertree (markdoc/transform ast (clj->js {:variables frontmatter :functions {:toHumanDate md-to-human-date}}))
        html (markdoc/renderers.html rendertree)]
    [html frontmatter]))

(defn make-templated-html [title content]
  (-> template
      (str/replace "{{ TITLE }}" title)
      (str/replace "{{ CONTENT }}" content)))

(defn process-post-path [post-path]
  (p/let [post (slurp post-path)
          [post-html frontmatter] (markdown-to-html post)
          templated-html (make-templated-html (:title frontmatter) post-html)
          slug (-> (path/dirname post-path)
                   (path/basename))]
    {:path post-path
     :slug slug
     :html templated-html}))

(defn build []
  (fs.emptyDir dist-folder)
  (p/let [posts (js->clj (glob "posts/**/*.md"))
          posts (p/all (map process-post-path posts))
          _ (p/all (map (fn [p] (let [post-path (:path p)
                                      destfolder (path/join dist-folder (:slug p))]
                                  (p/do
                                    (fs.emptyDir destfolder)
                                    (fs.copy (path/dirname post-path) destfolder)
                                    (fs.remove (path/join destfolder "index.md"))
                                    (fs.writeFile (path/join destfolder "index.html") (:html p)))))
                        posts))]
    posts))

(comment
  (await (build))
  nil)
