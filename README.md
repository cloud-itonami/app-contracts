# etzhayyim-project-contracts

全世界の契約情報を JSON-LD として構造化・管理するプロジェクト。

## Overview

このプロジェクトは、以下の契約関連エンティティを収集・管理します：

- **Organization**: 企業、政府機関、非営利団体などの組織
- **Person**: 自然人（公開データのみ、プライバシー保護）
- **Contract**: 商業契約、雇用契約、政府調達など
- **SocialContract**: 憲法、国際条約、社会規範

## Architecture

```
etzhayyim-project-contracts
│
├── data/
│   ├── schema/                 # JSON-LD スキーマ定義
│   │   ├── context.jsonld      # 共通 @context
│   │   ├── Organization.jsonld
│   │   ├── Person.jsonld
│   │   ├── Contract.jsonld
│   │   └── SocialContract.jsonld
│   │
│   ├── organizations/          # 組織データ (.jsonld)
│   ├── persons/                # 自然人データ (.jsonld)
│   ├── contracts/              # 契約データ (.jsonld)
│   └── social-contracts/       # 社会契約データ (.jsonld)
│
├── wasm/                       # App components
│   └── contracts-crawler-component/  # Crawler 連携 MCP
│
└── cdn/                        # 公開用静的サイト
    └── contracts-ui-*/         # Contract viewer UI
```

## Data Schema

### JSON-LD Context

全データは `data/schema/context.jsonld` で定義された @context を使用します。

- Base vocab: `https://etzhayyim.com/schema/contracts#`
- Schema.org: `https://schema.org/`
- GLEIF: `https://www.gleif.org/ontology/`

### Organization

```json
{
  "@context": "../schema/context.jsonld",
  "@type": "Organization",
  "@id": "https://etzhayyim.com/data/organizations/{id}",
  "name": "組織名",
  "legalName": "法的登録名",
  "identifier": "法人番号",
  "lei": "GLEIF LEI",
  "jurisdiction": "US",
  "foundingDate": "2000-01-01",
  "url": "https://example.com",
  "sameAs": ["https://opencorporates.com/...", "https://www.wikidata.org/wiki/..."]
}
```

### SocialContract

```json
{
  "@context": "../schema/context.jsonld",
  "@type": "SocialContract",
  "@id": "https://etzhayyim.com/data/social-contracts/{id}",
  "name": "契約名",
  "constitutionalType": "constitution",
  "jurisdiction": "JP",
  "adoptedDate": "1946-11-03",
  "effectiveDate": "1947-05-03",
  "scope": "適用範囲",
  "url": "https://example.gov/...",
  "sameAs": ["https://www.wikidata.org/wiki/..."]
}
```

## Data Collection

### etzhayyim-project-crawler 連携

etzhayyim-project-crawler を使用して以下のソースから収集：

1. **OpenCorporates** (https://opencorporates.com)
   - 企業情報、法人登録データ

2. **GLEIF** (https://www.gleif.org)
   - Legal Entity Identifier (LEI) データ

3. **Government Contract Registries**
   - 各国政府の調達契約データベース
   - 例: USAspending.gov, 日本政府調達情報

4. **Constitutional Texts**
   - Constitute Project (https://www.constituteproject.org)
   - 各国政府公式サイト

5. **International Treaties**
   - UN Treaty Collection
   - 国際条約データベース

### Crawler MCP Component

`wasm/contracts-crawler-component/` に App MCP として実装。

Endpoint: `https://{nanoid}.etzhayyim.com/api/mcp`

Tools:
- `collect_organizations`: 組織データ収集
- `collect_contracts`: 契約データ収集
- `collect_social_contracts`: 社会契約データ収集
- `verify_data`: データ検証・更新

## Publication

### etzhayyim-project-public-global への公開

収集されたデータは etzhayyim-project-public-global を通じて公開されます。

- 公開 API endpoint: `https://public.etzhayyim.com/contracts/`
- SPARQL endpoint: `https://public.etzhayyim.com/sparql`
- Linked Data: `https://etzhayyim.com/data/{type}/{id}.jsonld`

## Privacy & Compliance

### 個人情報保護

- **公開データのみ収集**: 公的に利用可能なデータソースのみ
- **GDPR/CCPA 遵守**: 個人データの取り扱いに注意
- **Right to be Forgotten**: 削除リクエストに対応

### Data Quality

- `confidence`: 0.0-1.0 の信頼度スコア
- `source`: データソースの明示
- `dateCollected`: 収集日時
- `lastVerified`: 最終検証日時

## Usage Examples

### Organization Query

```bash
curl https://etzhayyim.com/data/organizations/apple-inc.jsonld
```

### Social Contract Query

```bash
curl https://etzhayyim.com/data/social-contracts/japan-constitution.jsonld
```

### SPARQL Query (planned)

```sparql
PREFIX etzhayyim: <https://etzhayyim.com/schema/>
PREFIX schema: <https://schema.org/>

SELECT ?org ?name ?lei
WHERE {
  ?org a schema:Organization ;
       schema:name ?name ;
       gleif:hasLEI ?lei .
  FILTER(?lei != "")
}
```

## Development

### Adding New Data

1. データを適切なディレクトリに配置 (`data/{type}/`)
2. JSON-LD 形式で記述（`@context` は `../schema/context.jsonld` を参照）
3. `@id` は `https://etzhayyim.com/data/{type}/{id}` 形式
4. `source`, `dateCollected`, `confidence` フィールドを必ず含める

### Validation

```bash
# JSON-LD validation (planned)
bazel test //60-apps/etzhayyim-project-contracts/...
```

## Roadmap

- [ ] SPARQL endpoint 実装
- [ ] RDF triple store 連携 (Apache Jena / Blazegraph)
- [ ] 自動更新パイプライン (Tekton)
- [ ] データ品質ダッシュボード
- [ ] API rate limiting & access control
- [ ] データセット export (N-Triples, Turtle, RDF/XML)

## References

- Schema.org: https://schema.org
- JSON-LD: https://json-ld.org
- GLEIF: https://www.gleif.org
- OpenCorporates: https://opencorporates.com
- DoDAF v2: https://dodcio.defense.gov/Library/DoD-Architecture-Framework/
