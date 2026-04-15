# EPIC 2 — Target Architecture & Platform Foundation

## Objectif

Mettre en place le socle Kubernetes, sécurité, observabilité et middleware.

---

## Story 2.1 — Architecture cible Kogito sur Kubernetes

Voir `../docs/architecture-target.md`

**Checklist :**
- [ ] Namespaces créés : `kogito-platform`, `kogito-services`, `kogito-monitoring`, `kogito-security`
- [ ] Diagramme d'architecture validé par l'équipe
- [ ] Mode de déploiement GitOps/Helm choisi et documenté
- [ ] Dépendance à l'ancien Kogito Operator explicitement écartée

---

## Story 2.2 — Sécurité plateforme

Fichiers : `security/`

| Fichier | Contenu |
|---------|---------|
| `keycloak-realm.json` | Realm Keycloak avec clients, rôles, mappers |
| `rbac-kogito.yaml` | Roles et RoleBindings K8s |
| `network-policies.yaml` | NetworkPolicies (deny-all + allow explicite) |
| `external-secret.yaml` | ExternalSecrets (PostgreSQL, OIDC, Kafka) |

**Checklist :**
- [ ] Keycloak realm importé et fonctionnel
- [ ] OIDC opérationnel sur tous les services Quarkus
- [ ] RBAC Kubernetes appliqué
- [ ] Secrets externalisés (Vault / AWS SSM via ESO)
- [ ] NetworkPolicies déployées et testées
- [ ] TLS actif sur tous les Ingress exposés
- [ ] Rotation des secrets planifiée (refreshInterval ESO)

### Commandes de déploiement sécurité

```bash
# Créer les namespaces avec labels requis pour NetworkPolicies
kubectl create namespace kogito-services
kubectl label namespace kogito-services name=kogito-services

kubectl create namespace kogito-platform
kubectl label namespace kogito-platform name=kogito-platform

kubectl create namespace kogito-monitoring
kubectl label namespace kogito-monitoring name=kogito-monitoring

# Appliquer RBAC
kubectl apply -f security/rbac-kogito.yaml

# Appliquer NetworkPolicies
kubectl apply -f security/network-policies.yaml

# Appliquer ExternalSecrets (ESO requis)
kubectl apply -f security/external-secret.yaml

# Importer realm Keycloak
# (via API Keycloak ou Helm chart Keycloak)
```

---

## Story 2.3 — Observabilité

Fichiers : `observability/`

| Fichier | Contenu |
|---------|---------|
| `prometheus-servicemonitor.yaml` | ServiceMonitors pour Prometheus Operator |
| `alerting-rules.yaml` | Règles d'alertes (processus, Kafka, PG, Jobs Service) |
| `grafana-dashboard.json` | Dashboard SRE Kogito (à importer dans Grafana) |

**Checklist :**
- [ ] Micrometer/Prometheus activé dans tous les services Quarkus
- [ ] ServiceMonitors déployés et cibles visibles dans Prometheus
- [ ] Dashboards Grafana importés
- [ ] OpenTelemetry activé, traces visibles dans Jaeger/Tempo
- [ ] Alertes critiques configurées et testées

---

## Story 2.4 — Middleware cible

Fichiers : `middleware/`

| Fichier | Contenu |
|---------|---------|
| `postgresql-statefulset.yaml` | StatefulSet PostgreSQL 15 |
| `kafka-strimzi.yaml` | Kafka cluster via Strimzi + Topics + KafkaUser |
| `jobs-service-deploy.yaml` | Deployment Jobs Service Kogito |
| `data-index-deploy.yaml` | Deployment Data Index Kogito |

**Checklist :**
- [ ] PostgreSQL déployé avec PVC persistant
- [ ] Kafka déployé via Strimzi (3 brokers, 3 ZK)
- [ ] Topics Kogito créés automatiquement
- [ ] Jobs Service déployé et connecté à Kafka + PG
- [ ] Data Index déployé et indexant les événements Kafka
- [ ] Backups PostgreSQL configurés et testés
- [ ] SLIs/SLOs définis et mesurés

### Ordre de déploiement recommandé

```bash
# 1. PostgreSQL en premier
kubectl apply -f middleware/postgresql-statefulset.yaml

# 2. Kafka
kubectl apply -f middleware/kafka-strimzi.yaml

# 3. Attendre que Kafka soit prêt
kubectl wait kafka/kogito-kafka --for=condition=Ready --timeout=300s -n kogito-platform

# 4. Jobs Service
kubectl apply -f middleware/jobs-service-deploy.yaml

# 5. Data Index
kubectl apply -f middleware/data-index-deploy.yaml

# Vérification santé
kubectl get pods -n kogito-platform
kubectl logs deployment/kogito-jobs-service -n kogito-platform
kubectl logs deployment/kogito-data-index -n kogito-platform
```

---

## SLIs / SLOs de base

| Service | SLI | SLO |
|---------|-----|-----|
| Process Service | Disponibilité (uptime) | 99.5% mensuel |
| Jobs Service | Timers déclenchés dans les délais | 99.9% |
| Data Index | Latence GraphQL p99 | < 500ms |
| PostgreSQL | Disponibilité | 99.9% mensuel |
| Kafka | Consumer lag | < 10 000 messages |
