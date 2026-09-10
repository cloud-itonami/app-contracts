(ns contracts.corpus
  "disk 上の `data/` を読んで、`contracts.validate` が食べる corpus map にする。

  ここが **唯一 disk を触る場所**。壊れた JSON はここで捨てずに
  `:parse-errors` として持ち上げる —— 読めなかったファイルを黙って
  除くと、読めないことが「問題が無かった」と同じ形になる。"
  (:require ["node:fs" :as fs]
            ["node:path" :as path]
            [clojure.string :as str]
            [contracts.validate :as v]))

(def context-path "data/schema/context.jsonld")
(def schema-dir "data/schema")

(defn- walk
  "root からの相対 path で `.jsonld` を列挙する。"
  [root rel]
  (let [abs (path/join root rel)]
    (if-not (fs/existsSync abs)
      []
      (mapcat (fn [entry]
                (let [name (.-name entry)
                      child (if (str/blank? rel) name (str rel "/" name))]
                  (cond
                    (.isDirectory entry) (walk root child)
                    (str/ends-with? name ".jsonld") [child]
                    :else [])))
              (fs/readdirSync abs #js {:withFileTypes true})))))

(defn- read-json [root rel]
  (try
    {:path rel :doc (js->clj (js/JSON.parse (fs/readFileSync (path/join root rel) "utf8")))}
    (catch :default e
      {:path rel :error (or (.-message e) (str e))})))

(defn load-corpus
  "`:refused` を持って返るのは、corpus の入口そのものが読めなかったとき。
   その場合の finding 0 件は clean ではなく **未測定**である。"
  [root]
  (let [files (vec (walk root "data"))]
    (cond
      (empty? files)
      {:refused (str root "/data に .jsonld が 1 件も無い")}

      (not (contains? (set files) context-path))
      {:refused (str context-path " が無い。term を 1 つも解決できない")}

      :else
      (let [read     (mapv #(read-json root %) files)
            ok       (filterv :doc read)
            errors   (mapv (fn [{:keys [path error]}] {:path path :message error})
                           (filterv :error read))
            ctx-doc  (:doc (first (filter #(= context-path (:path %)) ok)))
            context  (get ctx-doc "@context")
            schema?  (fn [{:keys [path]}] (str/starts-with? path (str schema-dir "/")))
            classes  (->> ok
                          (filter schema?)
                          (remove #(= context-path (:path %)))
                          (mapv (fn [{:keys [path doc]}]
                                  {:term (str/replace (path/basename path) #"\.jsonld$" "")
                                   :path path
                                   :doc  doc})))
            records  (->> ok
                          (remove schema?)
                          (mapv (fn [{:keys [path doc]}]
                                  {:path path
                                   :dir  (path/basename (path/dirname path))
                                   :slug (str/replace (path/basename path) #"\.jsonld$" "")
                                   :doc  doc})))]
        (if-not (map? context)
          {:refused (str context-path " の @context が map ではない")}
          {:context      context
           :classes      classes
           :records      records
           :files        (set files)
           :parse-errors errors})))))

(defn check-root
  "load して check する。`:refused` はそのまま持ち上げる。"
  [root]
  (let [corpus (load-corpus root)]
    (if (:refused corpus)
      corpus
      (merge corpus (v/check corpus)))))
