# Grille de Scoring de Complexité de Migration

## Méthodologie

Chaque processus est évalué sur 8 dimensions. Le **score total** (0–100) détermine le lot de migration.

---

## Grille de scoring

| Dimension | Critère | Score |
|-----------|---------|-------|
| **Scripts JSR-223** | Aucun script | 0 |
| | Scripts simples (< 10 lignes, logique stateless) | 5 |
| | Scripts complexes ou avec injection de beans | 15 |
| **Java Delegates** | Aucun delegate | 0 |
| | Delegates simples (CRUD, appel REST) | 5 |
| | Delegates avec état, transactions, retry | 15 |
| **External Tasks** | Aucune | 0 |
| | 1–3 topics | 5 |
| | > 3 topics ou logique de polling custom | 10 |
| **User Tasks / Forms** | Aucune tâche humaine | 0 |
| | Formulaires Camunda natifs | 5 |
| | Formulaires externes (Angular, React) intégrés | 15 |
| **DMN / Décisions** | Aucun DMN | 0 |
| | DMN simple (1 table) | 3 |
| | DMN complexes ou chaînés | 10 |
| **Async Boundaries** | Aucun | 0 |
| | < 3 async boundaries | 5 |
| | ≥ 3 async ou compensation BPMN | 15 |
| **Connectors** | Aucun connector Camunda Connect | 0 |
| | Connectors standards (REST, SOAP) | 5 |
| | Connectors custom ou plugins propriétaires | 15 |
| **Variables / Sérialisation** | Variables simples (primitives, JSON) | 0 |
| | Sérialisation Java (Serializable) | 10 |
| | Types complexes, sérialisation custom | 15 |

---

## Interprétation du score

| Score | Niveau | Lot de migration |
|-------|--------|-----------------|
| 0 – 20 | 🟢 **Simple** | Lot 1 — Pilote (quick wins) |
| 21 – 45 | 🟡 **Modéré** | Lot 2 — Migration standard |
| 46 – 70 | 🟠 **Complexe** | Lot 3 — Migration avec refonte partielle |
| 71 – 100 | 🔴 **Critique** | Lot 4 — Refonte complète ou report |

---

## Template d'inventaire CSV

```csv
process_id,process_name,owner_business,owner_tech,version,has_scripts,script_score,has_delegates,delegate_score,has_external_tasks,ext_task_score,has_user_tasks,user_task_score,has_dmn,dmn_score,async_boundaries,async_score,has_connectors,connector_score,serialization_type,serial_score,total_score,complexity_level,migration_lot,blocker,quick_win,notes
order-process,Order Management,Commerce,dev-team-a,3.2,yes,5,yes,5,no,0,yes,5,yes,3,2,5,no,0,json,0,23,Modéré,2,none,no,Standard migration
claim-process,Claims,Insurance,dev-team-b,1.8,yes,15,yes,15,yes,10,no,0,no,0,3,15,yes,15,java,10,80,Critique,4,Java serialization,no,Full rewrite needed
```

---

## Heatmap de complexité (template Markdown)

```
HEATMAP DE MIGRATION — PROCESSUS CAMUNDA 7
===========================================

🟢 LOT 1 — PILOTE (Score 0-20)
  • simple-approval       [Score: 8]   Owner: team-a
  • document-routing      [Score: 12]  Owner: team-b

🟡 LOT 2 — STANDARD (Score 21-45)
  • order-process         [Score: 23]  Owner: team-a   ⚑ User forms à migrer
  • invoice-validation    [Score: 38]  Owner: team-c

🟠 LOT 3 — REFONTE PARTIELLE (Score 46-70)
  • credit-check          [Score: 52]  Owner: team-b   ⚑ Java delegates complexes
  • onboarding-flow       [Score: 61]  Owner: team-d   ⚑ Forms externes

🔴 LOT 4 — CRITIQUE (Score 71-100)
  • legacy-claim          [Score: 80]  Owner: team-b   🚫 Java serialization blocker
  • old-erp-integration   [Score: 94]  Owner: team-e   🚫 Plugin propriétaire custom

BLOCKERS IDENTIFIÉS :
  1. Java Serializable sur legacy-claim → Refonte du modèle de données requise
  2. Plugin propriétaire sur old-erp-integration → Réécriture en External Task Kogito

QUICK WINS :
  1. simple-approval : Score 8, pas de delegate, pur BPMN → Migration en 1 sprint
  2. document-routing : Score 12, DMN simple → Migration en 1 sprint
```
