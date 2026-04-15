# Plan de Rollback — Migration Camunda 7 → Kogito

## Niveaux de rollback

### Niveau 1 — Rollback de routage (< 5 minutes)

Bascule le router vers Camunda 7 sans toucher aux services ni aux données.

```bash
# Option A: via kubectl (si router deployé en K8s)
kubectl set env deployment/process-router \
  -n kogito-services \
  ROUTE_OVERRIDE=camunda \
  --record

# Option B: via feature flag (LaunchDarkly, Unleash, etc.)
curl -X PATCH https://feature-flags.internal/flags/kogito-enabled \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'

# Vérification immédiate
kubectl rollout status deployment/process-router -n kogito-services
curl -X GET https://api.internal/health/routing
```

**Quand utiliser** : Taux d'erreur > 5%, latence anormale, dégradation détectée.

---

### Niveau 2 — Rollback de déploiement (< 15 minutes)

Revient à la version précédente du service Kogito (Kubernetes rollout undo).

```bash
# Rollback d'un service processus
kubectl rollout undo deployment/order-process-service -n kogito-services

# Vérifier l'historique
kubectl rollout history deployment/order-process-service -n kogito-services

# Rollback vers une révision spécifique
kubectl rollout undo deployment/order-process-service \
  -n kogito-services \
  --to-revision=3

# Statut
kubectl rollout status deployment/order-process-service -n kogito-services
```

**Quand utiliser** : Bug introduit par une nouvelle version du service (pas du processus).

---

### Niveau 3 — Rollback de schéma DB (< 30 minutes)

Revient sur les migrations Liquibase.

```bash
# Via Liquibase CLI
liquibase \
  --url=jdbc:postgresql://postgresql-kogito:5432/kogito \
  --username=kogito \
  --password=${DB_PASSWORD} \
  --changelog-file=epic3-factory/persistence/liquibase-changelog.xml \
  rollback-to-tag v1.0

# Ou rollback d'un nombre de changesets
liquibase rollbackCount 1
```

**Prérequis** : Chaque changeset doit avoir un bloc `<rollback>` explicite.  
**Quand utiliser** : Migration de schéma causant des erreurs de données.

---

### Niveau 4 — Rollback complet de plateforme (> 1h, décision CODIR)

Démantèlement des services Kogito et retour à Camunda 7 comme moteur unique.

```bash
# Désactiver tous les services Kogito
kubectl scale deployment --replicas=0 -l app.kubernetes.io/part-of=kogito-migration \
  -n kogito-services

# Supprimer les routages
kubectl set env deployment/process-router \
  ROUTE_OVERRIDE=camunda \
  KOGITO_ENABLED=false

# Archiver les logs et métriques avant suppression
kubectl logs deployment/kogito-jobs-service > /backup/jobs-service-$(date +%Y%m%d).log
```

---

## Gestion des instances Kogito créées avant le rollback

```
Scénario: 150 instances ont démarré sur Kogito avant le rollback.
Elles sont en cours d'exécution dans la DB PostgreSQL Kogito.

Options :
  A) Laisser se terminer normalement sur Kogito (si stable)
     → Maintenir les services Kogito UP uniquement pour ces instances
     → Interdire nouvelles instances (ROUTE_OVERRIDE=camunda)

  B) Compenser manuellement
     → Identifier les instances en cours via Data Index GraphQL
     → Pour chaque instance: noter l'état (variables, nœud actif)
     → Créer une instance Camunda équivalente avec les mêmes variables
     → Annuler l'instance Kogito (abort)

  C) Migration d'urgence des instances (si processus critique)
     → Script de migration d'état Kogito → Camunda
     → À préparer en avance pour les processus critiques
```

### Query GraphQL pour lister les instances actives Kogito

```graphql
# Via Data Index GraphQL
query ActiveInstances {
  ProcessInstances(where: {state: {equal: ACTIVE}}) {
    id
    processId
    processName
    variables
    nodes {
      name
      type
      enter
    }
    lastUpdate
  }
}
```

---

## Runbook de décision

```
INCIDENT DÉTECTÉ
      │
      ▼
Taux d'erreur > 5% OU Latence p99 > 2x baseline?
      │ OUI
      ▼
Rollback Niveau 1 (routage) → Résolu en < 5min
      │ NON RÉSOLU
      ▼
Bug de version (nouveau déploiement récent)?
      │ OUI
      ▼
Rollback Niveau 2 (deployment rollout undo) → 15min
      │ NON RÉSOLU
      ▼
Bug de migration DB?
      │ OUI
      ▼
Rollback Niveau 3 (Liquibase rollback) → 30min
      │ NON RÉSOLU
      ▼
→ Escalade CODIR → Rollback Niveau 4 (décision)
```

---

## Contacts d'escalade

| Rôle | Contact | Disponibilité |
|------|---------|---------------|
| On-Call SRE | _à compléter_ | 24/7 |
| Lead Migration | _à compléter_ | Heures ouvrées |
| Owner Architecture | _à compléter_ | Heures ouvrées |
| Décideur CODIR | _à compléter_ | Sur escalade |
