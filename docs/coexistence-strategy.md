# Stratégie de Coexistence Camunda 7 / Kogito

## Principe retenu

> **Stratégie validée** : « Nouvelles instances → Kogito / Instances en cours → Camunda 7 »

Cette approche permet une migration progressive sans interruption de service.

---

## Mode de coexistence

```
                    ┌─────────────────────────────────┐
   Nouvelle         │                                 │
   demande ─────────►   ROUTER SERVICE (API Gateway)  │
                    │                                 │
                    └──────────┬──────────────────────┘
                               │
               ┌───────────────┴───────────────┐
               │                               │
               ▼                               ▼
   ┌───────────────────────┐     ┌───────────────────────┐
   │   KOGITO (Cible)      │     │   CAMUNDA 7 (Source)  │
   │                       │     │                       │
   │  Nouvelles instances  │     │  Instances actives    │
   │  Post date de cutover │     │  Pré date de cutover  │
   └───────────────────────┘     └───────────────────────┘
```

---

## Règles de routage

| Critère | Destination |
|---------|-------------|
| Nouvelle instance créée **après** la date de cutover du processus | **Kogito** |
| Instance en cours démarrée **avant** la date de cutover | **Camunda 7** |
| Reprise sur incident / compensation | Système d'origine |
| APIs d'administration (cockpit, task list) | Selon système d'instance |

### Implémentation du routage

```java
// Exemple : Router via un header HTTP ou un attribut de corrélation
@ApplicationScoped
public class ProcessRouter {

    @Inject
    CamundaClient camundaClient;

    @Inject
    KogitoClient kogitoClient;

    public ProcessInstanceRef startProcess(String processId, Map<String, Object> variables) {
        if (migrationRegistry.isMigrated(processId)) {
            return kogitoClient.startProcess(processId, variables);
        } else {
            return camundaClient.startProcess(processId, variables);
        }
    }
}
```

---

## Plan de cutover par processus

| Étape | Description | Responsable |
|-------|-------------|-------------|
| 1 | Validation du processus migré en staging | Dev + QA |
| 2 | Gel des nouvelles instances Camunda (feature flag) | Ops |
| 3 | Bascule du router vers Kogito pour ce processus | Ops |
| 4 | Suivi des instances Camunda restantes jusqu'à complétion | Support |
| 5 | Désactivation du processus dans Camunda 7 | Ops |

### Critères de bascule complète (par processus)

- [ ] Tests de non-régression passés (>95% coverage)
- [ ] Performance validée (p99 < seuil Camunda)
- [ ] Monitoring opérationnel (alertes, dashboards)
- [ ] Rollback testé et documenté
- [ ] Validation métier (UAT signé)
- [ ] 0 instance active sur Camunda pour ce processus

---

## Plan de rollback

### Déclencheurs de rollback

- Taux d'erreur > 5% sur les nouvelles instances Kogito
- Latence p99 > 2x la baseline Camunda
- Perte de données détectée
- Incident critique non résolu sous 2h

### Procédure de rollback

```bash
# 1. Basculer le router vers Camunda 7
kubectl set env deployment/process-router ROUTE_OVERRIDE=camunda

# 2. Vérifier que les nouvelles instances basculent sur Camunda
curl -X POST /health/routing-check

# 3. Alerter l'équipe de migration
# (webhook Slack/Teams configuré dans le router)

# 4. Analyser les instances Kogito créées pendant l'incident
# → Compenser manuellement si nécessaire
# → OU réinjecter dans Camunda avec les variables de contexte
```

---

## Stratégie de fin de vie Camunda 7

| Jallon | Condition |
|--------|-----------|
| Stop nouvelles instances sur Camunda | Feature flag activé pour tous les processus migrés |
| Fin de support Camunda | 0 instance active restante |
| Décommissionnement infrastructure | Post-migration complète + 3 mois de rétention données |

---

## Communication et gouvernance

- **Runbook** de bascule publié et partagé avant chaque cutover
- **War room** activé 24h après chaque bascule majeure
- **Revue post-cutover** systématique (blameless post-mortem si incident)
