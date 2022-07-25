(ns build
  "Blogengine, based on https://www.alexandercarls.de/markdoc-nbb-clojure/"
  (:require ["@markdoc/markdoc$default" :as markdoc :refer [Tag]]
            ["@sindresorhus/slugify$default" :as slugify]
            ["path" :as path]
            ["react$default" :as React]
            ["zx" :refer [glob fs]]
            [applied-science.js-interop :as j]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [nbb.core :refer [slurp await]]
            [promesa.core :as p]
            [reagent.core :as r]
            [reagent.dom.server :as srv]))

(def dist-folder "dist")
(def template (fs.readFileSync "template.html" "utf8"))

;; -----------------------------------------------------------------------------

(defn Heading [{:keys [id level children]}]
  (let [heading-tag
        (if (= level 1)
          [:h1.not-prose.text-2xl.font-extrabold.tracking-tight.text-slate-900.md:text-3xl {:id id} children]
          [(keyword (str "h" level)) {:id id :class "text-lg md:text-2xl"} children])]
    [:a.no-underline.relative {:href (str "#" id)} heading-tag]))

(def node-heading
  {:render "Heading"
   :children ["inline"]
   :attributes {:id {:type 'String}
                :level {:type 'Number :required true :default 1}}
   :transform (fn [node config]
                (let [attributes (.transformAttributes node config)
                      children (.transformChildren node config)
                      id (slugify (first children))]
                  (Tag.
                   "Heading"
                   (clj->js (into (js->clj attributes) {:id id}))
                   children)))})

;; -----------------------------------------------------------------------------

(defn date->human [date]
  (.toLocaleDateString date "en-US" #js {:year "numeric" :month "long" :day "numeric"}))
(def md-to-human-date {:transform (fn [parameters] (date->human (j/get parameters 0)))})

(defn parse-frontmatter [ast]
  (when-let [frontmatter (j/get-in ast [:attributes :frontmatter])]
    (edn/read-string frontmatter)))

(defn markdown-to-react-elements [markdown]
  (let [ast (markdoc/parse markdown)
        frontmatter (parse-frontmatter ast)
        rendertree (markdoc/transform ast (clj->js {:variables frontmatter
                                                    :functions {:toHumanDate md-to-human-date}
                                                    :nodes {:heading node-heading}}))
        react-elements (markdoc/renderers.react
                        rendertree
                        React
                        (clj->js {:components {"Heading" (r/reactify-component Heading)}}))]
    [react-elements frontmatter]))

(defn make-templated-html [title content]
  (-> template
      (str/replace "{{ TITLE }}" title)
      (str/replace "{{ CONTENT }}" content)))

(defn post-layout [date content]
  [:article.relative.pt-8.mt-6
   [:div.text-sm.leading-6
    [:dl
     [:dd.absolute.top-0.inset-x-0.text-slate-700
      [:time {:date-time (.toISOString date)} (date->human date)]]]]
   [:div.prose.prose-slate.max-w-none content]])

(defn process-post-path [post-path]
  (p/let [post (slurp post-path)
          [post-react-element frontmatter] (markdown-to-react-elements post)
          post-html (srv/render-to-static-markup (post-layout (:published-at frontmatter) post-react-element))
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
