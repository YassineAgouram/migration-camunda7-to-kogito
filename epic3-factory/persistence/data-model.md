# Modèle de Données Cible — Kogito

## Principes directeurs

1. **JSON-first** : toutes les variables de processus sont stockées en JSON
2. **Pas de Java Serialization** : `Serializable` est interdit pour les variables de processus
3. **Schéma versionné** : toute évolution passe par Liquibase
4. **Rétention explicite** : purge automatique des instances terminées après 90 jours (configurable)

---

## Format des variables de processus

### ✅ Types autorisés

```java
// Primitives et wrappers
String, Integer, Long, Double, Boolean

// Collections JSON-compatibles
List<String>, List<Map<String, Object>>, Map<String, Object>

// Records / POJOs avec Jackson (pas de cycle, pas de type générique raw)
public record OrderVariables(
    String orderId,
    String customerId,
    String status,
    List<OrderItem> items,
    Double totalAmount
) {}

// LocalDate, Instant, ZonedDateTime (via Jackson JSR-310)
LocalDate dueDate;
Instant createdAt;
```

### ❌ Types interdits

```java
// Java Serialization — INTERDIT
Object serializedBean; // Si la classe implémente Serializable sans @JsonSerialize

// Types opaques non-sérialisables
InputStream, Connection, EntityManager

// Références à des beans Spring/CDI
@Inject
PaymentService paymentService; // Ne jamais stocker dans une variable de processus
```

---

## Stratégie de sérialisation

### Configuration Jackson pour Kogito

```java
// src/main/java/com/example/kogito/config/JacksonConfig.java
@ApplicationScoped
public class JacksonConfig {

    void configure(@Observes ObjectMapper mapper) {
        // Activer JSR-310 (dates Java 8+)
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Ignorer les propriétés inconnues (compatibilité forward)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Inclure uniquement les champs non-null
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
```

---

## Mapping historique Camunda → Audit Kogito

| Camunda 7 (History) | Kogito (Audit Log) | Notes |
|---------------------|-------------------|-------|
| `HistoricProcessInstance` | `audit_log` (PROCESS_STARTED, PROCESS_COMPLETED) | — |
| `HistoricActivityInstance` | `audit_log` (NODE_ENTERED, NODE_EXITED) | — |
| `HistoricVariableInstance` | `audit_log` (VARIABLE_CHANGED) + `process_variables` | Variables actuelles dans `process_variables` |
| `HistoricTaskInstance` | `audit_log` (TASK_CREATED, TASK_COMPLETED) + `user_tasks` | — |
| `HistoricIncident` | `audit_log` (ERROR_OCCURRED) | — |
| History Level `FULL` | Tout activé par défaut | Configurer la rétention |
| History Level `NONE` | Désactiver l'audit event listener | — |

### Activation de l'audit listener Kogito

```java
@ApplicationScoped
public class KogitoAuditListener {

    @Inject
    AuditLogRepository auditRepo;

    public void onProcessStarted(@Observes ProcessStartedEvent event) {
        auditRepo.save(AuditEntry.builder()
            .eventType("PROCESS_STARTED")
            .processId(event.getProcessId())
            .processInstanceId(event.getProcessInstanceId())
            .eventDate(Instant.now())
            .build());
    }

    public void onNodeEntered(@Observes BeforeNodeTriggeredEvent event) {
        auditRepo.save(AuditEntry.builder()
            .eventType("NODE_ENTERED")
            .processInstanceId(event.getProcessInstance().getId())
            .nodeId(event.getNodeInstance().getNodeId().toString())
            .nodeName(event.getNodeInstance().getNodeName())
            .eventDate(Instant.now())
            .build());
    }
}
```

---

## Règles PII et data retention

| Donnée | Classification | Rétention | Traitement |
|--------|---------------|-----------|------------|
| `customerId` | PII - Indirect | 90 jours post-complétion | Pseudonymisation après 30j |
| `customerEmail` | PII - Direct | 90 jours post-complétion | Masquage dans les logs |
| `orderId` | Données métier | 5 ans | Archivage en base froide |
| `paymentId` | Données financières | 10 ans (légal) | Archivage sécurisé |
| Variables techniques | Interne | 30 jours | Purge automatique |

### Pseudonymisation des variables PII

```java
@ApplicationScoped
public class PiiMaskingFilter {

    private static final Set<String> PII_FIELDS = Set.of(
        "customerEmail", "customerPhone", "customerAddress", "iban"
    );

    public Map<String, Object> maskPii(Map<String, Object> variables) {
        return variables.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> PII_FIELDS.contains(e.getKey()) ? mask(e.getValue()) : e.getValue()
            ));
    }

    private Object mask(Object value) {
        if (value instanceof String s && s.length() > 4) {
            return s.substring(0, 2) + "****" + s.substring(s.length() - 2);
        }
        return "****";
    }
}
```

---

## Conventions DB et migrations

### Règles Liquibase

1. **Un changeset = une opération atomique**
2. **Jamais modifier un changeset existant** (sauf `runOnChange="true"` pour les vues/fonctions)
3. **Nommage** : `{numéro}-{description-kebab-case}` → `4-add-process-priority-column`
4. **Rollback obligatoire** pour les changesets critiques en production
5. **Tests** : tout changeset testé sur un H2 en CI avant production

### Template changeset

```xml
<changeSet id="N-description" author="equipe-migration" labels="sprint-N">
    <comment>Description courte de l'objectif</comment>

    <!-- Opération -->
    <addColumn tableName="process_instances">
        <column name="priority" type="INTEGER" defaultValue="0">
            <constraints nullable="false"/>
        </column>
    </addColumn>

    <!-- Rollback explicite -->
    <rollback>
        <dropColumn tableName="process_instances" columnName="priority"/>
    </rollback>
</changeSet>
```

---

## Stratégie de purge et rétention

```bash
# Purge manuelle (via kubectl exec ou job cron)
psql -c "SELECT purge_completed_instances(90);"

# Job Kubernetes Cron (quotidien à 2h)
# Voir epic2-platform/middleware/purge-cronjob.yaml
```

```yaml
# purge-cronjob.yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: kogito-data-purge
  namespace: kogito-platform
spec:
  schedule: "0 2 * * *"
  jobTemplate:
    spec:
      template:
        spec:
          containers:
            - name: purge
              image: postgres:15-alpine
              env:
                - name: PGPASSWORD
                  valueFrom:
                    secretKeyRef:
                      name: kogito-postgres-secret
                      key: QUARKUS_DATASOURCE_PASSWORD
              command:
                - psql
                - -h
                - postgresql-kogito.kogito-platform.svc
                - -U
                - kogito
                - -c
                - "SELECT purge_completed_instances(90);"
          restartPolicy: OnFailure
```
