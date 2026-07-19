# Contract Data Publication

## Overview

etzhayyim-project-contracts のデータは以下の方法で公開されます：

## 1. JSON-LD Files (Direct Access)

全データは JSON-LD として Git リポジトリに格納され、直接アクセス可能：

```
https://etzhayyim.com/data/{type}/{id}.jsonld
```

Examples:
- `https://etzhayyim.com/data/organizations/apple-inc.jsonld`
- `https://etzhayyim.com/data/social-contracts/japan-constitution.jsonld`
- `https://etzhayyim.com/data/contracts/example-procurement-001.jsonld`

## 2. CDN Distribution (Planned)

静的ファイルとして CDN 配信：

```
60-apps/etzhayyim-project-contracts/wasm/contracts-ui-{nanoid}/svelte/
```

Features:
- Browse and search contracts
- Visualize organization networks
- Timeline view for social contracts
- Export to RDF/Turtle/N-Triples

## 3. API Endpoint (via etzhayyim-project-public-global)

REST API for programmatic access:

```
GET https://public.etzhayyim.com/contracts/organizations/{id}
GET https://public.etzhayyim.com/contracts/social-contracts/{id}
GET https://public.etzhayyim.com/contracts/search?q={query}
```

## 4. SPARQL Endpoint (Future)

RDF triple store with SPARQL endpoint:

```
POST https://public.etzhayyim.com/sparql
Content-Type: application/sparql-query

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

## 5. Linked Data

全エンティティは RDF として他のデータセットとリンク可能：

- Wikidata links via `sameAs`
- OpenCorporates links
- GLEIF links
- Schema.org types

## Access Control

### Public Data

以下のデータは無制限公開：

- Organizations (public companies, government agencies)
- Social Contracts (constitutions, treaties)
- Public procurement contracts

### Private Data

以下のデータは制限付き公開：

- Personal information (GDPR/CCPA compliance)
- Confidential contracts
- Sensitive government data

## Data Updates

### Automatic Updates

- Daily: Organization data verification
- Weekly: Social contract updates
- Monthly: Full dataset revalidation

### Manual Updates

Pull requests to add/update data:

1. Fork repository
2. Add/update JSON-LD files in `data/`
3. Ensure `@context`, `source`, `dateCollected` fields are present
4. Submit PR with data source reference

## Data Quality

All published data includes:

- `source`: Original data source URL
- `dateCollected`: Collection timestamp
- `lastVerified`: Last verification timestamp
- `confidence`: 0.0-1.0 confidence score

## Licensing

### Data License

All collected data is licensed under **CC0 1.0 Universal (Public Domain)** where possible.

Some data may have different licenses:
- Government data: Usually public domain
- OpenCorporates: ODbL 1.0
- GLEIF: CC0 1.0

### Code License

MCP components and tools: MIT License

## Privacy Policy

We respect privacy and comply with:

- **GDPR** (General Data Protection Regulation)
- **CCPA** (California Consumer Privacy Act)
- **APPI** (Act on the Protection of Personal Information, Japan)

### Right to be Forgotten

Contact: privacy@etzhayyim.com

We will remove personal data upon request.

## Contact

- Issues: https://github.com/etzhayyim-ai/etzhayyim-root/issues
- Email: contracts@etzhayyim.com
- MCP Endpoint: https://q2whl5cx.etzhayyim.com/api/mcp

## Related Projects

- **etzhayyim-project-crawler**: Web scraping and data collection
- **etzhayyim-project-public-global**: Public API and data distribution
- **etzhayyim-project-performers-platform**: Platform management
