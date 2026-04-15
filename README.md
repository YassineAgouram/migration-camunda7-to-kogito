Voici les étapes complètes pour runner le projet, dans l'ordre exact :

🔧 Prérequis à installer
bash# Java 17+
java -version

# Maven 3.9+
mvn -version

# kubectl configuré sur votre cluster
kubectl cluster-info

# Helm 3.12+
helm version

# Python 3.8+ (pour les scripts Epic 1)
python3 --version

📁 ÉTAPE 0 — Extraire le ZIP
bashunzip camunda-kogito-migration.zip
cd camunda-kogito-migration/

🔍 ÉTAPE 1 — Discovery (Epic 1)
1.1 Scanner votre projet Camunda existant
bashcd epic1-discovery/scripts/
chmod +x inventory-bpmn.sh

# Pointer vers votre projet Camunda 7
./inventory-bpmn.sh /chemin/vers/votre-projet-camunda
# → Génère : ../outputs/inventory.csv
1.2 Calculer les scores de complexité
bashpython3 complexity-scorer.py --input ../outputs/inventory.csv --json
# → Génère : ../outputs/heatmap.md et ../outputs/scores.json
1.3 Lire la heatmap
bashcat ../outputs/heatmap.md
# → Identifie les Quick Wins (Lot 1) et les Blockers (Lot 4)

☸️ ÉTAPE 2 — Préparer Kubernetes (Epic 2)
2.1 Créer les namespaces
bashkubectl create namespace kogito-platform
kubectl create namespace kogito-services
kubectl create namespace kogito-monitoring
kubectl create namespace kogito-security

# Labels requis pour les NetworkPolicies
kubectl label namespace kogito-platform   name=kogito-platform
kubectl label namespace kogito-services   name=kogito-services
kubectl label namespace kogito-monitoring name=kogito-monitoring
kubectl label namespace kogito-security   name=kogito-security
2.2 Installer les opérateurs prérequis
bash# Strimzi (Kafka Operator)
helm repo add strimzi https://strimzi.io/charts/
helm install strimzi-operator strimzi/strimzi-kafka-operator \
  -n kogito-platform

# External Secrets Operator (pour les secrets Vault)
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets \
  -n kogito-security

# Prometheus + Grafana (Kube Prometheus Stack)
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install kube-prometheus prometheus-community/kube-prometheus-stack \
  -n kogito-monitoring

# cert-manager (TLS)
helm repo add jetstack https://charts.jetstack.io
helm install cert-manager jetstack/cert-manager \
  -n kogito-security \
  --set installCRDs=true
2.3 Déployer la sécurité
bashcd epic2-platform/

# RBAC
kubectl apply -f security/rbac-kogito.yaml

# NetworkPolicies
kubectl apply -f security/network-policies.yaml

# Secrets (adapter l'URL Vault d'abord dans external-secret.yaml)
kubectl apply -f security/external-secret.yaml

# Keycloak — importer le realm (si Keycloak déjà déployé)
# Via UI: http://keycloak/admin → Import → security/keycloak-realm.json
# Ou via CLI:
/opt/keycloak/bin/kcadm.sh create realms \
  -s enabled=true \
  -f security/keycloak-realm.json
2.4 Déployer le middleware (dans l'ordre !)
bash# 1. PostgreSQL
kubectl apply -f middleware/postgresql-statefulset.yaml -n kogito-platform

# Attendre que PostgreSQL soit prêt
kubectl wait pod -l app=postgresql \
  --for=condition=Ready --timeout=120s -n kogito-platform

# 2. Kafka
kubectl apply -f middleware/kafka-strimzi.yaml -n kogito-platform

# Attendre que Kafka soit prêt (peut prendre 2-3 min)
kubectl wait kafka/kogito-kafka \
  --for=condition=Ready --timeout=300s -n kogito-platform

# 3. Jobs Service
kubectl apply -f middleware/jobs-service-deploy.yaml -n kogito-platform

# 4. Data Index
kubectl apply -f middleware/data-index-deploy.yaml -n kogito-platform

# Vérification globale
kubectl get pods -n kogito-platform
2.5 Déployer l'observabilité
bashkubectl apply -f observability/prometheus-servicemonitor.yaml -n kogito-monitoring
kubectl apply -f observability/alerting-rules.yaml -n kogito-monitoring

# Vérifier que Prometheus scrape bien les cibles
kubectl port-forward svc/kube-prometheus-prometheus 9090:9090 -n kogito-monitoring
# → ouvrir http://localhost:9090/targets

🏗️ ÉTAPE 3 — Générer et lancer un service Kogito (Epic 3)
3.1 Générer un service depuis le template
bashcd epic3-factory/templates/process-service/
chmod +x bootstrap.sh

./bootstrap.sh \
  --name mon-processus \
  --package com.macompanie.mondomaine
# → Génère : ./mon-processus/
3.2 Appliquer le schéma de base de données
bashcd mon-processus/

# En local avec Docker (dev rapide)
docker run -d \
  --name kogito-postgres \
  -e POSTGRES_DB=kogito \
  -e POSTGRES_USER=kogito \
  -e POSTGRES_PASSWORD=kogito \
  -p 5432:5432 \
  postgres:15-alpine

# Appliquer le schema Liquibase
mvn liquibase:update \
  -Dliquibase.url=jdbc:postgresql://localhost:5432/kogito \
  -Dliquibase.username=kogito \
  -Dliquibase.password=kogito \
  -Dliquibase.changeLogFile=../../epic3-factory/persistence/liquibase-changelog.xml
3.3 Lancer Kafka en local (dev)
bash# Via Docker Compose (rapide pour le dev)
docker run -d \
  --name kogito-kafka \
  -p 9092:9092 \
  -e KAFKA_CFG_ZOOKEEPER_CONNECT=localhost:2181 \
  bitnami/kafka:3.6
3.4 Lancer le service en mode dev Quarkus
bashcd mon-processus/

# Mode dev (hot-reload, DevUI intégré)
./mvnw quarkus:dev

# → Service disponible sur http://localhost:8080
# → DevUI sur http://localhost:8080/q/dev
# → Métriques sur http://localhost:8080/q/metrics
# → Health sur http://localhost:8080/q/health
3.5 Tester le processus via API REST
bash# Démarrer une instance de processus
curl -X POST http://localhost:8080/order-management-v1 \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-001",
    "customerId": "CUST-42",
    "customerEmail": "client@example.com",
    "items": [
      {"productId": "PROD-1", "qty": 2, "price": 49.99}
    ]
  }'

# Lister les instances actives
curl http://localhost:8080/order-management-v1

# Voir une instance spécifique
curl http://localhost:8080/order-management-v1/{id}

🚀 ÉTAPE 4 — Déployer en staging/production
4.1 Builder l'image Docker
bashcd mon-processus/

# Build JVM
./mvnw package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm \
  -t mon-registry/mon-processus:1.0.0 .

# Push
docker push mon-registry/mon-processus:1.0.0
4.2 Déployer sur Kubernetes
bash# Créer le Deployment K8s (adapter l'image)
kubectl create deployment mon-processus \
  --image=mon-registry/mon-processus:1.0.0 \
  -n kogito-services

# Exposer le service
kubectl expose deployment mon-processus \
  --port=80 --target-port=8080 \
  -n kogito-services

# Appliquer les variables d'environnement (secrets)
kubectl set env deployment/mon-processus \
  --from=secret/kogito-postgres-secret \
  --from=secret/kogito-oidc-credentials \
  --from=secret/kogito-kafka-secret \
  -n kogito-services

# Vérifier
kubectl rollout status deployment/mon-processus -n kogito-services
kubectl logs deployment/mon-processus -n kogito-services -f

✅ Vérifications finales
bash# Santé de tous les pods
kubectl get pods -A -l app.kubernetes.io/part-of=kogito-migration

# Logs Jobs Service (timers)
kubectl logs deployment/kogito-jobs-service -n kogito-platform

# Logs Data Index (événements Kafka reçus)
kubectl logs deployment/kogito-data-index -n kogito-platform

# GraphQL Data Index (instances visibles ?)
curl -X POST http://data-index.kogito.example.com/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ ProcessInstances { id processId status } }"}'

# Grafana dashboard
kubectl port-forward svc/kube-prometheus-grafana 3000:80 -n kogito-monitoring
# → http://localhost:3000 (admin/prom-operator)

⚡ Résumé de l'ordre
1. Extraire ZIP
2. Epic 1 → inventory-bpmn.sh + complexity-scorer.py
3. K8s namespaces + opérateurs (Strimzi, ESO, Prometheus)
4. Sécurité (RBAC, NetworkPolicies, Keycloak)
5. Middleware : PostgreSQL → Kafka → Jobs Service → Data Index
6. Observabilité : ServiceMonitors + Alerting
7. bootstrap.sh → générer service → mvnw quarkus:dev (dev local)
8. Liquibase → schéma DB
9. Tests API REST
10. Build Docker → deploy K8s staging → prod

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
