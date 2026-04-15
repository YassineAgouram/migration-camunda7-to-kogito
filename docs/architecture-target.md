# Architecture Cible — Kogito sur Kubernetes

## Décision d'architecture (ADR-001)

**Statut** : Proposé  
**Date** : 2024  
**Contexte** : Migration Camunda 7 vers Kogito (runtime Quarkus) sur Kubernetes

---

## Diagramme d'architecture cible

```
┌─────────────────────────────────────────────────────────────────┐
│                    NAMESPACE: kogito-platform                    │
│                                                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌───────────────────┐  │
│  │  Data Index  │    │ Jobs Service │    │   Keycloak/OIDC   │  │
│  │  (GraphQL)   │◄───│  (Quarkus)   │    │   (Auth Server)   │  │
│  └──────┬───────┘    └──────┬───────┘    └───────────────────┘  │
│         │                  │                                     │
│  ┌──────▼───────────────────▼──────┐                            │
│  │         Apache Kafka            │                            │
│  │   (Strimzi Operator)            │                            │
│  └──────────────────────────────────┘                           │
│                                                                  │
│  ┌──────────────────────────────────┐                           │
│  │         PostgreSQL               │                           │
│  │  (process-data, jobs, audit)     │                           │
│  └──────────────────────────────────┘                           │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  NAMESPACE: kogito-services                      │
│                                                                  │
│  ┌─────────────────────┐    ┌─────────────────────┐            │
│  │  Process Service A  │    │  Process Service B  │            │
│  │  (Quarkus + Kogito) │    │  (Quarkus + Kogito) │            │
│  │  BPMN: order.bpmn   │    │  BPMN: claim.bpmn   │            │
│  └──────────┬──────────┘    └──────────┬──────────┘            │
│             │ CloudEvents               │                       │
│  ┌──────────▼──────────────────────────▼──────────┐            │
│  │              Kafka Topics                       │            │
│  │  kogito.process.{id}.{signal}                  │            │
│  └─────────────────────────────────────────────────┘           │
│                                                                  │
│  ┌─────────────────────┐    ┌─────────────────────┐            │
│  │  Decision Service   │    │  Worker Service      │            │
│  │  (Quarkus + DMN)    │    │  (External Task)     │            │
│  └─────────────────────┘    └─────────────────────┘            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  NAMESPACE: kogito-monitoring                    │
│  Prometheus │ Grafana │ Jaeger/Tempo │ AlertManager             │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        INGRESS / GATEWAY                         │
│          Nginx Ingress Controller + TLS (cert-manager)          │
└─────────────────────────────────────────────────────────────────┘
```

---

## Namespaces

| Namespace | Contenu | Accès |
|-----------|---------|-------|
| `kogito-platform` | Kafka, PostgreSQL, Jobs Service, Data Index | Équipe plateforme |
| `kogito-services` | Services processus et décision métier | Équipe dev |
| `kogito-monitoring` | Prometheus, Grafana, Jaeger | Équipe SRE |
| `kogito-security` | Keycloak, cert-manager | Équipe sécurité |

---

## Composants et rôles

### Jobs Service
- Gère les **timers** et les **événements temporels** BPMN
- Remplace la gestion native des jobs Camunda
- Persistance dans PostgreSQL
- Communication via Kafka (CloudEvents)

### Data Index
- **Index GraphQL** des instances de processus et tâches humaines
- Source de vérité pour les requêtes opérationnelles
- Abonné aux topics Kafka des services processus

### Kafka (Strimzi)
- **Bus événementiel** central (CloudEvents spec)
- Topics : `kogito.process.{serviceId}.{signalOrEvent}`
- Remplace les signaux internes Camunda

---

## Mode de déploiement GitOps

```
git-repo/
├── helm/
│   ├── kogito-platform/     ← Chart plateforme (Kafka, PG, Jobs, DataIndex)
│   └── kogito-services/     ← Chart services métier (1 release par service)
├── kustomize/
│   ├── base/
│   └── overlays/
│       ├── dev/
│       ├── staging/
│       └── prod/
└── argocd/
    └── applications/        ← ArgoCD Applications
```

---

## Décisions clés

| Décision | Choix | Raison |
|----------|-------|--------|
| Runtime | Quarkus 3.x (JVM mode) | Compatibilité librairies, outillage mature |
| Kogito operator | **Non retenu** | Déprécié, remplacé par Helm direct |
| Persistance | PostgreSQL | Robustesse, support Kogito natif |
| Messaging | Kafka (Strimzi) | Standard CloudEvents, scalabilité |
| Auth | Keycloak + OIDC | Intégration Quarkus OIDC native |
| Tracing | OpenTelemetry → Jaeger | Standard CNCF |
