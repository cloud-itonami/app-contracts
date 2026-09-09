(ns contracts.validate
  "この corpus が満たしていなければならないことを、値として答える。

  ここには **disk が出てこない**。入力は `contracts.corpus` が組み立てた
  in-memory の corpus map で、出力は finding の列と「何件を見たか」の数である。
  実データだけを入力にした検査は、実データが踏まない枝を永久に測らないまま
  緑を出す —— この seam が在るので test は合成 corpus を渡せる。

  finding は必ず `:rule` を持つ。呼び出し側は「何か落ちた」ではなく
  「名指しした理由で落ちた」を assert すること。upstream が理由名を変えたときに
  test が落ちるのは欠点ではなく、その assertion の効き目そのものである。"
  (:require [clojure.string :as str]))

;; ── この repo の規約（README `Development / Adding New Data` が正本）────────
;;
;; データは `data/{directory}/{slug}.jsonld` に置き、`@id` は
;; `https://etzhayyim.com/data/{directory}/{slug}` とする。publication 面は
;; `https://etzhayyim.com/data/{type}/{id}.jsonld` を serve するので、
;; **`@id` の slug とファイル名が違うと、その `@id` を辿っても record に届かない。**

(def id-base "https://etzhayyim.com/data/")

(def record-directories
  "record を置くディレクトリと、そこに入る class の対応。"
  {"organizations"    "Organization"
   "persons"          "Person"
   "contracts"        "Contract"
   "social-contracts" "SocialContract"})

(def reference-terms
  "値が corpus 内の別 record の `@id` である term。"
  ["parties" "signatories"])

(def ordered-date-pairs
  "先に来なければならない term と、後に来なければならない term。
   **両辺が同じ xsd 型のものだけを並べる** —— `xsd:date` と `xsd:dateTime` を
   文字列比較すると `2026-01-01` < `2026-01-01T00:00:00Z` が真になり、
   同じ瞬間が順序違反として報告される。"
  [["adoptedDate"   "effectiveDate"]
   ["effectiveDate" "expirationDate"]
   ["effectiveDate" "terminationDate"]
   ["foundingDate"  "dissolutionDate"]
   ["dateCollected" "lastVerified"]])

;; ── IRI 展開 ────────────────────────────────────────────────────────────────

(defn- prefix-map
  "`@context` のうち prefix 宣言だけを取る。JSON-LD の規約どおり
   「値が gen-delim で終わる素の文字列」を prefix とみなす。"
  [context]
  (into {} (keep (fn [[k v]]
                   (when (and (string? k)
                              (not (str/starts-with? k "@"))
                              (string? v)
                              (re-find #"[#/:]$" v))
                     [k v])))
        context))

(defn- term-definition [context term]
  (let [v (get context term)]
    (cond
      (string? v) {:id v}
      (map? v)    {:id (get v "@id") :type (get v "@type") :container (get v "@container")}
      :else       nil)))

(defn expand-iri
  "compact IRI / term / 絶対 IRI を絶対 IRI にする。展開できないものはそのまま返す。

  展開してから比べるのは、同じ名前空間を別の prefix で書いた 2 つが
  文字列としては違うため。**比較の前に正規化しないと、同じものが違うと報告される。**"
  [context s]
  (let [pm    (prefix-map context)
        vocab (get context "@vocab")]
    (cond
      (not (string? s)) s
      (re-find #"^https?://" s) s
      :else
      (let [i (str/index-of s ":")]
        (cond
          (and i (contains? pm (subs s 0 i)))
          (str (get pm (subs s 0 i)) (subs s (inc i)))

          (some? (get-in (term-definition context s) [:id]))
          (recur context (:id (term-definition context s)))

          (and (nil? i) (string? vocab)) (str vocab s)
          :else s)))))

(defn terms-for-iri
  "その IRI を意味する `@context` の term を全部返す。

  class 定義は property を **IRI** で宣言し、record は **term** で書く。
  この向きの解決を持たない検査は、required を 1 件も見られない。"
  [context iri]
  (let [target (expand-iri context iri)]
    (vec (sort (keep (fn [[k _]]
                       (when (and (string? k)
                                  (not (str/starts-with? k "@"))
                                  (some? (:id (term-definition context k)))
                                  (= target (expand-iri context k)))
                         k))
                     context)))))

;; ── 値の形 ──────────────────────────────────────────────────────────────────

(def ^:private days-in-month [31 28 31 30 31 30 31 31 30 31 30 31])

(defn- leap-year? [y]
  (and (zero? (mod y 4))
       (or (not (zero? (mod y 100))) (zero? (mod y 400)))))

(defn calendar-date?
  "`YYYY-MM-DD` であり、かつ **実在する日**であること。
   形だけを見る検査は `2026-02-30` を通す。"
  [s]
  (boolean
   (when (and (string? s) (re-matches #"\d{4}-\d{2}-\d{2}" s))
     (let [y (parse-long (subs s 0 4))
           m (parse-long (subs s 5 7))
           d (parse-long (subs s 8 10))]
       (and (<= 1 m 12)
            (<= 1 d (if (and (= m 2) (leap-year? y))
                      29
                      (nth days-in-month (dec m)))))))))

(defn instant?
  "`xsd:dateTime`。日付部分は calendar-date? と同じ厳しさで見る。"
  [s]
  (boolean
   (when (and (string? s)
              (re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})" s))
     (let [hh (parse-long (subs s 11 13))
           mm (parse-long (subs s 14 16))
           ss (parse-long (subs s 17 19))]
       (and (calendar-date? (subs s 0 10))
            (<= hh 23) (<= mm 59) (<= ss 60))))))

(def ^:private lei-alphabet
  (zipmap "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ" (range 36)))

(defn lei-valid?
  "ISO 17442 の LEI。20 文字であることと、mod-97-10 の検査数字が合うこと。

  長さだけを見る検査は 1 文字の打ち間違いを通す —— LEI は他所の台帳を指す
  外部キーなので、通った打ち間違いは黙って別の法人を指すか、誰も指さない。"
  [s]
  (boolean
   (and (string? s)
        (= 20 (count s))
        (every? #(contains? lei-alphabet %) s)
        (= 1 (reduce (fn [r ch]
                       (let [v (get lei-alphabet ch)]
                         (if (< v 10)
                           (mod (+ (* r 10) v) 97)
                           (mod (+ (* r 100) v) 97))))
                     0 s)))))

(defn currency-code? [s]
  (boolean (and (string? s) (re-matches #"[A-Z]{3}" s))))

;; ── path ────────────────────────────────────────────────────────────────────

(defn resolve-relative
  "`data/organizations` から `../schema/context.jsonld` を辿った先。"
  [base-dir rel]
  (->> (concat (remove str/blank? (str/split base-dir #"/"))
               (remove str/blank? (str/split rel #"/")))
       (reduce (fn [acc s]
                 (cond (= s ".")  acc
                       (= s "..") (if (seq acc) (pop acc) acc)
                       :else      (conj acc s)))
               [])
       (str/join "/")))

(defn- parent-dir [p]
  (let [i (str/last-index-of p "/")]
    (if i (subs p 0 i) "")))

;; ── 検査 ────────────────────────────────────────────────────────────────────

(defn- finding [rule path detail]
  {:rule rule :path path :detail detail})

(defn check-parse-errors [corpus]
  (mapv (fn [{:keys [path message]}]
          (finding :json/unparseable path message))
        (:parse-errors corpus)))

(defn check-class-identity
  "`@context` の class term が、その class を定義しているファイルの `@id` と
  同じものを指していること。

  ずれると **class 定義に書かれた `required` はどの instance にも届かない** ——
  record は term を書き、term は別の class に展開されるので、制約を持つ class の
  instance は corpus に 1 件も存在しないことになる。制約が消えるのではなく、
  誰も見ない場所へ移る。だから検査を持たないかぎり何の音もしない。"
  [{:keys [context classes]}]
  (vec (keep (fn [{:keys [term path doc]}]
               (let [declared (get doc "@id")
                     from-ctx (:id (term-definition context term))]
                 (cond
                   (nil? from-ctx)
                   (finding :class/absent-from-context path
                            (str "class term " (pr-str term) " が @context に無い"))

                   (not= (expand-iri context from-ctx) (expand-iri context declared))
                   (finding :class/identity-split path
                            (str "@context は " (pr-str term) " を "
                                 (pr-str (expand-iri context from-ctx))
                                 " と展開するが、この class 定義は自身を "
                                 (pr-str (expand-iri context declared)) " と名乗る")))))
             classes)))

(defn check-record-id-matches-path
  [{:keys [records]}]
  (vec (keep (fn [{:keys [path dir slug doc]}]
               (let [want (str id-base dir "/" slug)
                     got  (get doc "@id")]
                 (when (not= want got)
                   (finding :id/does-not-match-path path
                            (str "@id " (pr-str got) " / この path が serve される先は "
                                 (pr-str want))))))
             records)))

(defn check-context-resolves
  [{:keys [records classes files]}]
  (vec (keep (fn [{:keys [path doc]}]
               (let [c (get doc "@context")]
                 (cond
                   (nil? c) (finding :context/absent path "@context が無い")
                   (string? c)
                   (let [target (resolve-relative (parent-dir path) c)]
                     (when-not (contains? files target)
                       (finding :context/unresolvable path
                                (str "@context " (pr-str c) " -> " target " は corpus に無い")))))))
             (concat records classes))))

(defn check-record-type
  [{:keys [records classes]}]
  (let [known (set (map :term classes))]
    (vec (keep (fn [{:keys [path dir doc]}]
                 (let [t (get doc "@type")
                       expected (get record-directories dir)]
                   (cond
                     (not (contains? known t))
                     (finding :type/undefined-class path
                              (str "@type " (pr-str t) " を定義した class が data/schema に無い"))

                     (and expected (not= t expected))
                     (finding :type/wrong-directory path
                              (str "@type " (pr-str t) " だが " dir "/ が持つのは "
                                   (pr-str expected))))))
               records))))

(defn- required-iris [class-doc]
  (->> (get class-doc "properties")
       (filter #(true? (get % "required")))
       (mapv #(get % "@id"))))

(defn check-required-properties
  [{:keys [context records classes]}]
  (let [by-term (into {} (map (juxt :term :doc)) classes)]
    (vec (mapcat (fn [{:keys [path doc]}]
                   (when-let [cls (get by-term (get doc "@type"))]
                     (keep (fn [iri]
                             (let [terms (terms-for-iri context iri)]
                               (cond
                                 (empty? terms)
                                 (finding :required/term-missing-from-context path
                                          (str "class が必須と宣言した " (pr-str iri)
                                               " を書くための term が @context に無い"))

                                 (not-any? #(some? (get doc %)) terms)
                                 (finding :required/absent path
                                          (str "必須の " (pr-str iri) " が無い（term: "
                                               (str/join ", " terms) "）")))))
                           (required-iris cls))))
                 records))))

(defn check-references-resolve
  [{:keys [records]}]
  (let [ids (into #{} (keep #(get (:doc %) "@id")) records)]
    (vec (mapcat (fn [{:keys [path doc]}]
                   (mapcat (fn [term]
                             (keep (fn [r]
                                     (when-not (contains? ids r)
                                       (finding :ref/dangling path
                                                (str term " -> " r " という record は corpus に無い"))))
                                   (let [v (get doc term)]
                                     (cond (vector? v) v
                                           (string? v) [v]
                                           :else       []))))
                           reference-terms))
                 records))))

(defn check-typed-values
  "`@context` が term に宣言した型を、値が実際に満たしていること。"
  [{:keys [context records]}]
  (vec (mapcat
        (fn [{:keys [path doc]}]
          (keep (fn [[term v]]
                  (let [t (:type (term-definition context term))]
                    (cond
                      (and (= t "xsd:date") (not (calendar-date? v)))
                      (finding :value/not-a-date path (str term " = " (pr-str v)))

                      (and (= t "xsd:dateTime") (not (instant? v)))
                      (finding :value/not-an-instant path (str term " = " (pr-str v)))

                      (and (= t "xsd:float")
                           (not (and (number? v) (<= 0 v) (<= v 1))))
                      (finding :value/confidence-out-of-range path
                               (str term " = " (pr-str v) " は 0..1 の外"))

                      (and (= term "lei") (not (lei-valid? v)))
                      (finding :value/lei-invalid path
                               (str "lei " (pr-str v) " は ISO 17442 を満たさない"))

                      (and (= term "currency") (not (currency-code? v)))
                      (finding :value/currency-not-iso4217 path
                               (str "currency " (pr-str v))))))
                (filter (fn [[k _]] (not (str/starts-with? (str k) "@"))) doc)))
        records)))

(defn- comparable-temporal-pair?
  "両辺が **同じ種類の、形の整った**時間値であること。

  形が壊れている値を順序比較すると、`:date/out-of-order` が出る ——
  だがその record の問題は順序ではなく値である。名乗っている理由と違う理由で
  拒否する検査は、拒否できたことの証拠にならない。形の欠陥は
  `check-typed-values` が `:value/not-a-date` として 1 度だけ報告する。"
  [x y]
  (or (and (calendar-date? x) (calendar-date? y))
      (and (instant? x) (instant? y))))

(defn check-date-order
  [{:keys [records]}]
  (vec (mapcat (fn [{:keys [path doc]}]
                 (keep (fn [[a b]]
                         (let [x (get doc a) y (get doc b)]
                           ;; 同じ日に採択され施行された条文は在る。**`<` にすると
                           ;; それを違反として報告する。** 境界は通さなければならない
                           (when (and (comparable-temporal-pair? x y)
                                      (pos? (compare x y)))
                             (finding :date/out-of-order path
                                      (str a " (" x ") が " b " (" y ") より後")))))
                       ordered-date-pairs))
               records)))

(defn check-class-examples
  "class 定義が自分で載せている example が、その class の宣言を満たしていること。

  `@id` の解決先は見ない —— example は説明であって corpus の member ではないので、
  実在しない `@id` を挙げてよい。見るのは `@type` と required だけ。"
  [{:keys [context classes]}]
  (vec (mapcat
        (fn [{:keys [term path doc]}]
          (mapcat (fn [[i ex]]
                    (let [where (str path " example[" i "]")]
                      (concat
                       (when (not= term (get ex "@type"))
                         [(finding :example/type-mismatch where
                                   (str "@type " (pr-str (get ex "@type"))
                                        " だが、この class は " (pr-str term)))])
                       (keep (fn [iri]
                               (let [terms (terms-for-iri context iri)]
                                 (when (and (seq terms)
                                            (not-any? #(some? (get ex %)) terms))
                                   (finding :example/required-absent where
                                            (str "必須の " (pr-str iri) " が無い")))))
                             (required-iris doc)))))
                  (map-indexed vector (get doc "examples"))))
        classes)))

(def checks
  [check-parse-errors
   check-class-identity
   check-context-resolves
   check-record-id-matches-path
   check-record-type
   check-required-properties
   check-references-resolve
   check-typed-values
   check-date-order
   check-class-examples])

(defn check
  "corpus 全体を見て、finding と **何件を見たか** を返す。

  `:checked` を返すのは、finding が空であることに 2 つの意味が在るため ——
  「見て、問題が無かった」と「何も見ていない」。呼び出し側はこの数で
  その 2 つを分ける。"
  [corpus]
  {:findings (vec (mapcat #(% corpus) checks))
   :checked  {:records          (count (:records corpus))
              :classes          (count (:classes corpus))
              :files            (count (:files corpus))
              :examples         (reduce + 0 (map #(count (get (:doc %) "examples")) (:classes corpus)))
              :cross-references (reduce + 0
                                        (for [{:keys [doc]} (:records corpus)
                                              term reference-terms]
                                          (count (let [v (get doc term)]
                                                   (cond (vector? v) v
                                                         (string? v) [v]
                                                         :else       [])))))
              :records-by-directory
              (reduce (fn [m {:keys [dir]}] (update m dir (fnil inc 0)))
                      {} (:records corpus))}})

(defn findings-by-rule [result]
  (reduce (fn [m {:keys [rule]}] (update m rule (fnil inc 0))) {} (:findings result)))

;; ── 報告してよい判定 ────────────────────────────────────────────────────────

(def evidence-floor
  "この数を下回ったとき、対応する検査は **1 件も比較していない**。

  finding が空であることには 2 つの意味が在る —— 見て問題が無かったのと、
  何も見なかったの。後者を pass として返すと、corpus を全部消した tree が
  健全な tree と同じ値を返す。だから下回ったら pass ではなく refuse を返す。"
  {:records          1
   :classes          1
   :cross-references 1
   :examples         1})

(defn verdict
  "`:pass` / `:fail` / `:refused` のどれか。**`:refused` は 0 でも 1 でもない**
   という 3 値目であって、fail の一種ではない。"
  [{:keys [findings checked] :as _result}]
  (let [starved (into {} (keep (fn [[k floor]]
                                 (let [n (get checked k 0)]
                                   (when (< n floor) [k n])))
                               evidence-floor))]
    (cond
      (seq starved)  {:status :refused :starved starved}
      (seq findings) {:status :fail :findings findings}
      :else          {:status :pass :checked checked})))
