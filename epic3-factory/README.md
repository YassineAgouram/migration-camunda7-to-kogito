# EPIC 3 — Migration Factory & Standards

## Objectif

Industrialiser la migration avec conventions, templates et composants réutilisables.

---

## Story 3.1 — Standards de modélisation BPMN/DMN

Voir `standards/bpmn-dmn-conventions.md`

Checklist :
- [x] Conventions de nommage (ID, Name, Variables)
- [x] Patterns de remplacement des extensions Camunda (Delegates, Scripts, External Tasks)
- [x] Stratégies d'erreur BPMN standardisées
- [x] Conventions de timers (ISO 8601)
- [x] Conventions d'événements et corrélation (Signal, Message)
- [x] Conventions de versionning process/DMN

---

## Story 3.2 — Templates Quarkus/Kogito

### Templates disponibles

| Template | Localisation | Description |
|----------|-------------|-------------|
| `process-service` | `templates/process-service/` | Service processus BPMN complet avec REST, OIDC, Kafka, PG |
| `decision-service` | `templates/decision-service/` | Service DMN avec API REST de décision |
| `worker-kafka` | `templates/worker-kafka/` | Worker réactif Kafka (remplace External Task) |

### Utilisation d'un template

```bash
# Copier le template et adapter
cp -r templates/process-service/ my-order-service/
cd my-order-service/

# Adapter le pom.xml
sed -i 's/kogito-process-service-template/my-order-service/g' pom.xml

# Renommer le package Java
find src/ -name "*.java" | xargs sed -i 's/com.example.kogito/com.mycompany.orders/g'

# Démarrer en mode dev
./mvnw quarkus:dev
```

---

## Story 3.3 — Persistance et données

Voir `persistence/data-model.md` et `persistence/liquibase-changelog.xml`

Checklist :
- [x] Modèle de variables cible défini (JSON-first, no Java Serialization)
- [x] Politique de sérialisation Jackson définie
- [x] Mapping historique Camunda → Audit Kogito documenté
- [x] Tables et schémas Liquibase créés
- [x] Stratégie de purge et rétention (90 jours par défaut, CronJob K8s)
- [x] Règles PII et data retention documentées

---

## Checklist de migration d'un processus

```
[ ] 1. Inventaire du processus (Story 1.1)
[ ] 2. Score de complexité calculé (Story 1.2)
[ ] 3. BPMN converti selon standards (Story 3.1)
      [ ] Extensions Camunda remplacées
      [ ] Java Delegates → Work Item Handlers
      [ ] Scripts → MVEL ou Service Beans
      [ ] External Tasks → Kafka workers
[ ] 4. Tests unitaires créés (KogitoQuarkusScenario)
[ ] 5. Tests d'intégration passants
[ ] 6. Déploiement staging validé
[ ] 7. UAT signé
[ ] 8. Monitoring configuré (alertes, dashboards)
[ ] 9. Rollback documenté
[ ] 10. Cutover planifié et communiqué
```
