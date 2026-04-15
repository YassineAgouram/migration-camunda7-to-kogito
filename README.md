# Migration Camunda 7 → Kogito/Quarkus sur Kubernetes

## Vue d'ensemble

Ce projet couvre la migration complète d'un environnement Camunda 7 vers Kogito (Quarkus) déployé sur Kubernetes, organisé en **3 Epics** :

| Epic | Objectif |
|------|----------|
| **EPIC 1** — Discovery & Assessment | Inventorier l'existant, évaluer la complexité, définir la coexistence |
| **EPIC 2** — Target Architecture & Platform | Socle K8s, sécurité OIDC, observabilité, middleware |
| **EPIC 3** — Migration Factory & Standards | Conventions, templates Quarkus/Kogito, persistance |

---

## Structure du projet

```
camunda-kogito-migration/
├── README.md                        ← Ce fichier
├── docs/
│   ├── architecture-target.md       ← Diagramme & décisions d'architecture
│   ├── coexistence-strategy.md      ← Stratégie Camunda/Kogito en parallèle
│   ├── complexity-scoring-grid.md   ← Grille de scoring de migration
│   └── rollback-plan.md             ← Plan de rollback
├── epic1-discovery/
│   ├── README.md
│   ├── scripts/
│   │   ├── inventory-bpmn.sh        ← Recense les BPMN/DMN dans un repo
│   │   └── complexity-scorer.py     ← Score de complexité par processus
│   └── outputs/
│       ├── inventory-template.xlsx  ← Template d'inventaire (CSV)
│       └── heatmap-template.md      ← Template de heatmap de complexité
├── epic2-platform/
│   ├── README.md
│   ├── security/
│   │   ├── keycloak-realm.json      ← Realm Keycloak exemple
│   │   ├── rbac-kogito.yaml         ← RBAC Kubernetes
│   │   ├── network-policies.yaml    ← NetworkPolicies K8s
│   │   └── external-secret.yaml     ← ExternalSecret (ESO)
│   ├── observability/
│   │   ├── prometheus-servicemonitor.yaml
│   │   ├── grafana-dashboard.json   ← Dashboard SRE Kogito
│   │   └── alerting-rules.yaml      ← Règles d'alerting Prometheus
│   └── middleware/
│       ├── postgresql-statefulset.yaml
│       ├── kafka-strimzi.yaml
│       ├── jobs-service-deploy.yaml
│       └── data-index-deploy.yaml
├── epic3-factory/
│   ├── README.md
│   ├── standards/
│   │   ├── bpmn-dmn-conventions.md  ← Conventions de modélisation
│   │   └── naming-conventions.md    ← Naming et versioning
│   ├── persistence/
│   │   ├── data-model.md            ← Modèle de données cible
│   │   └── liquibase-changelog.xml  ← Migrations DB via Liquibase
│   └── templates/
│       ├── process-service/         ← Template service processus Kogito
│       ├── decision-service/        ← Template service DMN
│       ├── worker-kafka/            ← Template worker Kafka
│       ├── rest-client/             ← Template REST client sécurisé
│       ├── persistence/             ← Template persistance PostgreSQL
│       └── observability/           ← Template observabilité Quarkus
```

---

## Prérequis

| Outil | Version minimale |
|-------|-----------------|
| Java | 17+ |
| Quarkus | 3.8+ |
| Kogito | 2.x (Quarkus runtime) |
| Kubernetes | 1.27+ |
| Helm | 3.12+ |
| Kafka (Strimzi) | 0.39+ |
| PostgreSQL | 14+ |
| Keycloak | 23+ |

---

## Démarrage rapide

### 1. Cloner et lancer l'inventaire
```bash
cd epic1-discovery/scripts
chmod +x inventory-bpmn.sh
./inventory-bpmn.sh /path/to/camunda-project
python3 complexity-scorer.py --input ../outputs/inventory.csv
```

### 2. Déployer le socle plateforme
```bash
# Namespace
kubectl create namespace kogito-platform

# Middleware
kubectl apply -f epic2-platform/middleware/

# Sécurité
kubectl apply -f epic2-platform/security/

# Observabilité
kubectl apply -f epic2-platform/observability/
```

### 3. Générer un service Kogito depuis un template
```bash
cd epic3-factory/templates/process-service
./bootstrap.sh --name mon-processus --package com.example
```

---

## Stratégie de migration (résumé)

```
Phase 1: Discovery (Epic 1)
  └─ Inventaire complet → Score de complexité → Prioritisation des lots

Phase 2: Socle (Epic 2)
  └─ K8s namespaces → Keycloak/OIDC → Kafka → PostgreSQL → Monitoring

Phase 3: Migration Factory (Epic 3)
  └─ Standards BPMN/DMN → Templates → Pilote (processus simple)
     └─ Lots de migration par complexité croissante

Coexistence: Nouvelles instances → Kogito / Instances en cours → Camunda 7
Cutover: Critères de bascule définis dans docs/coexistence-strategy.md
```

---

## Contact & gouvernance

- **Owner architecture** : _à compléter_
- **Owner migration** : _à compléter_
- **Revues** : Sprint review + ADR (Architecture Decision Records) dans `docs/`
