(ns contracts.validate-test
  "この corpus の不変条件を固定する。

  ## 実データだけを入力にしない

  実 corpus は 7 record しか無く、**どの検査の否定側も踏まない**。実データだけで
  書いた test は、検査を丸ごと削っても緑のままになる。だから各検査には
  合成 corpus を 1 つずつ当て、**その検査が名乗っている理由で拒否したこと**を
  rule の literal で assert する。結果だけを見る負テストは、別の原因で落ちた
  実行を『discriminate した』として数えてしまう。

  ## 比較には境界ちょうどの入力を置く

  `<=` を `<` に反転しても、線上のケースが無ければ緑のまま通る。採択と施行が
  同じ日の条文、confidence がちょうど 0 と 1、20 文字ちょうどの LEI、
  400 で割り切れる閏年 —— 通る側の境界を置いて初めて演算子が見える。"
  (:require [cljs.test :as t :refer [deftest is testing]]
            [contracts.corpus :as corpus]
            [contracts.validate :as v]))

;; ── 合成 corpus ─────────────────────────────────────────────────────────────

(def base-context
  {"@vocab"             "https://etzhayyim.com/schema/contracts#"
   "schema"             "https://schema.org/"
   "etzhayyim"          "https://etzhayyim.com/schema/"
   "xsd"                "http://www.w3.org/2001/XMLSchema#"
   "gleif"              "https://www.gleif.org/ontology/"
   "Organization"       {"@id" "etzhayyim:Organization" "@type" "@id"}
   "SocialContract"     {"@id" "etzhayyim:SocialContract" "@type" "@id"}
   "name"               "schema:name"
   "lei"                {"@id" "gleif:hasLEI" "@type" "xsd:string"}
   "currency"           "schema:priceCurrency"
   "constitutionalType" "etzhayyim:constitutionalType"
   "foundingDate"       {"@id" "schema:foundingDate" "@type" "xsd:date"}
   "dissolutionDate"    {"@id" "schema:dissolutionDate" "@type" "xsd:date"}
   "adoptedDate"        {"@id" "etzhayyim:adoptedDate" "@type" "xsd:date"}
   "effectiveDate"      {"@id" "etzhayyim:effectiveDate" "@type" "xsd:date"}
   "signatories"        {"@id" "etzhayyim:signatories" "@type" "@id" "@container" "@set"}
   "confidence"         {"@id" "etzhayyim:confidence" "@type" "xsd:float"}
   "dateCollected"      {"@id" "etzhayyim:dateCollected" "@type" "xsd:dateTime"}
   "lastVerified"       {"@id" "etzhayyim:lastVerified" "@type" "xsd:dateTime"}})

(def base-classes
  [{:term "Organization"
    :path "data/schema/Organization.jsonld"
    :doc  {"@context"   "./context.jsonld"
           "@id"        "etzhayyim:Organization"
           "properties" [{"@id" "schema:name" "required" true}
                         {"@id" "gleif:hasLEI"}]
           "examples"   [{"@context" "./context.jsonld"
                          "@type"    "Organization"
                          "name"     "Example Org"}]}}
   {:term "SocialContract"
    :path "data/schema/SocialContract.jsonld"
    :doc  {"@context"   "./context.jsonld"
           "@id"        "etzhayyim:SocialContract"
           "properties" [{"@id" "schema:name" "required" true}
                         {"@id" "etzhayyim:constitutionalType" "required" true}]
           "examples"   [{"@context"           "./context.jsonld"
                          "@type"              "SocialContract"
                          "name"               "Example Charter"
                          "constitutionalType" "treaty"}]}}])

(def base-records
  [{:path "data/organizations/acme.jsonld"
    :dir  "organizations" :slug "acme"
    :doc  {"@context"      "../schema/context.jsonld"
           "@type"         "Organization"
           "@id"           "https://etzhayyim.com/data/organizations/acme"
           "name"          "Acme"
           "lei"           "HWUPKR0MPOU8FGXBT394"
           "foundingDate"  "1976-04-01"
           "dateCollected" "2026-02-17T00:00:00Z"
           "lastVerified"  "2026-02-17T00:00:00Z"
           "confidence"    1}}
   {:path "data/social-contracts/acme-charter.jsonld"
    :dir  "social-contracts" :slug "acme-charter"
    :doc  {"@context"           "../schema/context.jsonld"
           "@type"              "SocialContract"
           "@id"                "https://etzhayyim.com/data/social-contracts/acme-charter"
           "name"               "Acme Charter"
           "constitutionalType" "charter"
           "adoptedDate"        "1976-04-01"
           "effectiveDate"      "1976-06-01"
           "signatories"        ["https://etzhayyim.com/data/organizations/acme"]}}])

(def base-files
  #{"data/schema/context.jsonld"
    "data/schema/Organization.jsonld"
    "data/schema/SocialContract.jsonld"
    "data/organizations/acme.jsonld"
    "data/social-contracts/acme-charter.jsonld"})

(defn corpus
  "健全な合成 corpus。overrides で 1 箇所だけ壊して 1 つの rule を出す。"
  [& {:as overrides}]
  (merge {:context      base-context
          :classes      base-classes
          :records      base-records
          :files        base-files
          :parse-errors []}
         overrides))

(defn rules
  "出た rule の集合。**集合の等価**で assert する —— 別の理由で落ちた実行が
   『拒否できた』として数えられるのを防ぐ。"
  [c]
  (set (map :rule (:findings (v/check c)))))

(defn- with-record-doc
  "1 件目の record の doc に f を当てた corpus。"
  [f]
  (corpus :records (update-in base-records [0 :doc] f)))

;; ── 合成 corpus の健全性そのもの ────────────────────────────────────────────

(deftest the-synthetic-corpus-is-clean-to-begin-with
  ;; これが赤いとき、以下の負テストが緑でも何も証明していない
  (is (= #{} (rules (corpus))))
  (is (= :pass (:status (v/verdict (v/check (corpus)))))))

;; ── 実 corpus ───────────────────────────────────────────────────────────────

(deftest the-real-corpus-on-disk-passes-every-check
  (let [r (corpus/check-root ".")]
    (is (nil? (:refused r))
        (str "corpus の入口が読めなかった: " (:refused r)))
    (is (= [] (:findings r))
        (str "実 corpus の finding: " (pr-str (:findings r))))
    (is (= :pass (:status (v/verdict r))))))

(deftest the-real-corpus-is-large-enough-for-the-checks-to-mean-anything
  ;; 参照が 0 本の corpus に対しては、参照検査は「無傷」と「空」を区別できない
  (let [{:keys [checked]} (corpus/check-root ".")]
    (is (<= 7 (:records checked)))
    (is (<= 4 (:classes checked)))
    (is (<= 4 (:cross-references checked)))
    (is (<= 5 (:examples checked)))
    (is (every? pos? (vals (:records-by-directory checked))))))

;; ── 何も見ていない実行を pass にしない ──────────────────────────────────────

(deftest an-empty-corpus-is-refused-not-passed
  (let [empty-c (corpus :records [] :classes [] :files #{})
        {:keys [status starved]} (v/verdict (v/check empty-c))]
    (is (= :refused status)
        "record も class も無い corpus は finding 0 件になる。それは clean ではない")
    (is (= #{:records :classes :cross-references :examples} (set (keys starved))))))

(deftest a-corpus-that-lost-every-cross-reference-is-refused
  ;; record は在るが参照が消えた —— :ref/dangling は以後どんな退行も見られない
  (let [c (corpus :records (mapv #(update % :doc dissoc "signatories") base-records))]
    (is (= :refused (:status (v/verdict (v/check c)))))))

(deftest a-corpus-with-findings-fails-rather-than-refusing
  ;; refuse は fail の一種ではない。取り違えると、壊れた corpus が「未測定」に化ける
  (is (= :fail (:status (v/verdict (v/check (with-record-doc #(assoc % "confidence" 2))))))))

;; ── class の同一性 ──────────────────────────────────────────────────────────

(deftest a-class-the-context-expands-elsewhere-is-reported
  ;; この repo で実際に起きていた形。context が Organization を
  ;; schema:Organization と展開する一方、class 定義は etzhayyim:Organization を
  ;; 名乗っていた。required はどの instance にも届かない
  (is (= #{:class/identity-split}
         (rules (corpus :context (assoc base-context "Organization"
                                        {"@id" "schema:Organization" "@type" "@id"}))))))

(deftest the-same-namespace-under-a-different-prefix-is-not-a-split
  ;; 展開せずに文字列で比べる検査は、これを誤って報告する
  (is (= #{} (rules (corpus :context (-> base-context
                                         (assoc "ez" "https://etzhayyim.com/schema/")
                                         (assoc "Organization"
                                                {"@id" "ez:Organization" "@type" "@id"})))))))

(deftest a-class-absent-from-the-context-is-reported
  (is (= #{:class/absent-from-context}
         (rules (corpus :context (dissoc base-context "Organization"))))))

;; ── @id と path ─────────────────────────────────────────────────────────────

(deftest an-id-whose-slug-is-not-the-filename-is-reported
  ;; この repo で実際に起きていた形。publication 面は
  ;; https://etzhayyim.com/data/{type}/{id}.jsonld を serve するので、
  ;; slug がファイル名と違う record は自分の @id から辿れない。
  ;;
  ;; **:ref/dangling を巻き込むのは正しい** —— @id は他 record からの参照先
  ;; そのものなので、動かせば charter の signatories は本当に解決しなくなる。
  ;; 巻き込みを隠すために assertion を緩めず、期待集合として宣言する
  (is (= #{:id/does-not-match-path :ref/dangling}
         (rules (with-record-doc #(assoc % "@id" "https://etzhayyim.com/data/organizations/acme-001"))))))

(deftest an-id-in-the-wrong-directory-is-reported
  (is (= #{:id/does-not-match-path :ref/dangling}
         (rules (with-record-doc #(assoc % "@id" "https://etzhayyim.com/data/contracts/acme"))))))

;; ── @context の解決 ─────────────────────────────────────────────────────────

(deftest a-context-that-points-at-no-file-is-reported
  (is (= #{:context/unresolvable}
         (rules (with-record-doc #(assoc % "@context" "../schema/missing.jsonld"))))))

(deftest a-record-without-a-context-is-reported
  (is (= #{:context/absent} (rules (with-record-doc #(dissoc % "@context"))))))

(deftest relative-context-paths-resolve-through-dot-dot
  (is (= "data/schema/context.jsonld"
         (v/resolve-relative "data/organizations" "../schema/context.jsonld")))
  (is (= "data/schema/context.jsonld"
         (v/resolve-relative "data/schema" "./context.jsonld"))))

;; ── @type ───────────────────────────────────────────────────────────────────

(deftest a-type-no-class-defines-is-reported
  (is (= #{:type/undefined-class} (rules (with-record-doc #(assoc % "@type" "Vehicle"))))))

(deftest a-record-filed-under-the-wrong-directory-is-reported
  ;; :required/absent も出るのが正しい —— SocialContract を名乗った以上、
  ;; その class の必須である constitutionalType は本当に無い
  (is (= #{:type/wrong-directory :required/absent}
         (rules (with-record-doc #(assoc % "@type" "SocialContract"))))))

;; ── required ────────────────────────────────────────────────────────────────

(deftest a-missing-required-property-is-reported
  (is (= #{:required/absent} (rules (with-record-doc #(dissoc % "name"))))))

(deftest a-required-property-no-term-can-express-is-reported
  ;; class が必須と言っている IRI を書く手段が context に無い。**この場合
  ;; required 検査は以後どの record も落とせない** ので、不在そのものを報告する
  (is (= #{:required/term-missing-from-context}
         (rules (corpus :context (dissoc base-context "name"))))))

(deftest required-is-resolved-through-the-context-not-by-name
  ;; class は IRI で、record は term で書く。term を改名しても、context が
  ;; 同じ IRI へ展開している限り required は満たされている
  (let [rename #(-> % (dissoc "name") (assoc "title" (get % "name")))]
    (is (= #{} (rules (corpus :context (-> base-context (dissoc "name") (assoc "title" "schema:name"))
                              :records (mapv #(update % :doc rename) base-records)
                              :classes (mapv #(update-in % [:doc "examples"]
                                                         (fn [xs] (mapv rename xs)))
                                             base-classes)))))))

;; ── 参照 ────────────────────────────────────────────────────────────────────

(deftest a-reference-to-a-record-that-does-not-exist-is-reported
  (is (= #{:ref/dangling}
         (rules (corpus :records (assoc-in base-records [1 :doc "signatories"]
                                           ["https://etzhayyim.com/data/organizations/ghost"]))))))

(deftest a-reference-written-as-a-bare-string-is-still-checked
  ;; @container @set なので普通は配列だが、1 件を裸で書いた record は在りうる。
  ;; vector しか見ない検査はそれを黙って飛ばす
  (is (= #{:ref/dangling}
         (rules (corpus :records (assoc-in base-records [1 :doc "signatories"]
                                           "https://etzhayyim.com/data/organizations/ghost"))))))

;; ── 値の形（境界つき）───────────────────────────────────────────────────────

(deftest confidence-accepts-both-ends-of-its-range
  ;; `<=` を `<` に反転させたとき、**この 2 件だけ**が赤くなる
  (is (= #{} (rules (with-record-doc #(assoc % "confidence" 0)))))
  (is (= #{} (rules (with-record-doc #(assoc % "confidence" 1))))))

(deftest confidence-outside-zero-to-one-is-reported
  (is (= #{:value/confidence-out-of-range} (rules (with-record-doc #(assoc % "confidence" 1.0001)))))
  (is (= #{:value/confidence-out-of-range} (rules (with-record-doc #(assoc % "confidence" -0.0001)))))
  (is (= #{:value/confidence-out-of-range} (rules (with-record-doc #(assoc % "confidence" "1"))))
      "文字列の \"1\" は数として比べられない。number? を落とすとこれが通る"))

(deftest a-date-that-is-not-on-the-calendar-is-reported
  (is (= #{:value/not-a-date} (rules (with-record-doc #(assoc % "foundingDate" "1976-02-30")))))
  (is (= #{:value/not-a-date} (rules (with-record-doc #(assoc % "foundingDate" "1976-13-01")))))
  (is (= #{:value/not-a-date} (rules (with-record-doc #(assoc % "foundingDate" "1976/04/01"))))))

(deftest the-leap-year-rule-is-the-gregorian-one-not-just-divisible-by-four
  (is (= #{} (rules (with-record-doc #(assoc % "foundingDate" "2024-02-29"))))
      "4 で割り切れる年")
  (is (= #{} (rules (with-record-doc #(assoc % "foundingDate" "2000-02-29"))))
      "400 で割り切れる年 —— mod 400 の節を落とすとここが赤くなる")
  (is (= #{:value/not-a-date} (rules (with-record-doc #(assoc % "foundingDate" "2100-02-29"))))
      "100 で割り切れて 400 では割り切れない年 —— mod 100 の節を落とすとここが通る")
  (is (= #{:value/not-a-date} (rules (with-record-doc #(assoc % "foundingDate" "2026-02-29"))))))

(deftest an-instant-must-carry-a-timezone-and-a-real-clock-time
  (is (= #{} (rules (with-record-doc #(assoc % "dateCollected" "2026-02-17T00:00:00+09:00")))))
  (is (= #{:value/not-an-instant} (rules (with-record-doc #(assoc % "dateCollected" "2026-02-17")))))
  (is (= #{:value/not-an-instant} (rules (with-record-doc #(assoc % "dateCollected" "2026-02-17T00:00:00")))))
  (is (= #{:value/not-an-instant} (rules (with-record-doc #(assoc % "dateCollected" "2026-02-17T24:00:00Z")))))
  (is (= #{:value/not-an-instant} (rules (with-record-doc #(assoc % "dateCollected" "2026-02-30T00:00:00Z"))))
      "instant の日付部分も暦で見る。**順序違反は道連れにしない** ——
       形の壊れた値は順序の問題ではないので :date/out-of-order は出さない"))

(deftest a-lei-is-checked-by-its-check-digits-not-only-its-length
  (is (v/lei-valid? "HWUPKR0MPOU8FGXBT394") "20 文字ちょうど、ISO 17442 適合")
  (is (= #{:value/lei-invalid} (rules (with-record-doc #(assoc % "lei" "HWUPKR0MPOU8FGXBT395"))))
      "末尾 1 文字だけ違う。長さは 20 のままなので、長さしか見ない検査は通す")
  (is (= #{:value/lei-invalid} (rules (with-record-doc #(assoc % "lei" "HWUPKR0MPOU8FGXBT39"))))
      "19 文字")
  (is (= #{:value/lei-invalid} (rules (with-record-doc #(assoc % "lei" "HWUPKR0MPOU8FGXBT3944"))))
      "21 文字")
  (is (= #{:value/lei-invalid} (rules (with-record-doc #(assoc % "lei" "hwupkr0mpou8fgxbt394"))))
      "小文字。LEI は大文字のみ"))

(deftest a-currency-must-be-a-three-letter-code
  (is (= #{} (rules (with-record-doc #(assoc % "currency" "JPY")))))
  (is (= #{:value/currency-not-iso4217} (rules (with-record-doc #(assoc % "currency" "jpy")))))
  (is (= #{:value/currency-not-iso4217} (rules (with-record-doc #(assoc % "currency" "JPYE")))))
  (is (= #{:value/currency-not-iso4217} (rules (with-record-doc #(assoc % "currency" "¥"))))))

;; ── 日付の順序（境界つき）───────────────────────────────────────────────────

(deftest adoption-and-entry-into-force-on-the-same-day-is-allowed
  ;; 同日に採択され施行された条文は在る。`<=` を `<` にするとここだけが赤くなる
  (is (= #{} (rules (corpus :records (assoc-in base-records [1 :doc "adoptedDate"] "1976-06-01"))))))

(deftest a-malformed-date-is-not-also-reported-as-out-of-order
  ;; 1 つの欠陥は 1 つの rule で報告する。壊れた値を順序比較すると、
  ;; 順序の検査が『自分が名乗っていない理由』で拒否したことになる
  (is (= #{:value/not-a-date}
         (rules (with-record-doc #(assoc % "foundingDate" "9999-99-99")))))
  (is (= #{:value/not-a-date}
         (rules (-> (with-record-doc #(assoc % "foundingDate" "2026-02-30"))
                    (update :records assoc-in [0 :doc "dissolutionDate"] "1900-01-01"))))
      "形が壊れているほうが先に報告され、順序は黙る"))

(deftest a-date-pair-in-the-wrong-order-is-reported
  (is (= #{:date/out-of-order}
         (rules (corpus :records (assoc-in base-records [1 :doc "adoptedDate"] "1976-06-02")))))
  (is (= #{:date/out-of-order}
         (rules (with-record-doc #(assoc % "dissolutionDate" "1900-01-01")))))
  (is (= #{:date/out-of-order}
         (rules (with-record-doc #(assoc % "lastVerified" "2020-01-01T00:00:00Z"))))))

;; ── class 定義自身の example ────────────────────────────────────────────────

(deftest an-example-that-does-not-satisfy-its-own-class-is-reported
  (is (= #{:example/required-absent}
         (rules (corpus :classes (update-in base-classes [1 :doc "examples" 0]
                                            dissoc "constitutionalType")))))
  (is (= #{:example/type-mismatch}
         (rules (corpus :classes (assoc-in base-classes [0 :doc "examples" 0 "@type"]
                                           "Person"))))))

(deftest an-example-may-cite-an-id-that-is-not-in-the-corpus
  ;; example は説明であって corpus の member ではない。ここを :ref/dangling に
  ;; すると、schema の例示を書けなくなる
  (is (= #{} (rules (corpus :classes (assoc-in base-classes [1 :doc "examples" 0 "signatories"]
                                               ["https://etzhayyim.com/data/organizations/nasa"]))))))

;; ── 読めなかったファイル ────────────────────────────────────────────────────

(deftest a-file-that-did-not-parse-is-reported-not-dropped
  ;; 読めなかったものを黙って除くと、壊れた JSON が「問題が無かった」と同じ形になる
  (is (= #{:json/unparseable}
         (rules (corpus :parse-errors [{:path "data/organizations/broken.jsonld"
                                        :message "Unexpected token }"}])))))

;; ── 実行 ────────────────────────────────────────────────────────────────────

(defmethod t/report [::t/default :end-run-tests] [m]
  (let [r (corpus/check-root ".")]
    (println)
    (println (str "SCANNED\trecords=" (get-in r [:checked :records] 0)
                  " classes=" (get-in r [:checked :classes] 0)
                  " examples=" (get-in r [:checked :examples] 0)
                  " cross-references=" (get-in r [:checked :cross-references] 0)))
    (println (str "verdict\t" (name (:status (v/verdict r)))))
    (println (str "tests\t" (:test m) " / assertions " (+ (:pass m) (:fail m) (:error m))
                  " / " (:fail m) " failures, " (:error m) " errors"))
    (if (and (t/successful? m) (= :pass (:status (v/verdict r))))
      (println "contracts-validate: OK")
      (do (println "contracts-validate: NOT OK")
          (set! (.-exitCode js/process) 1)))))

(t/run-tests (t/empty-env) 'contracts.validate-test)
