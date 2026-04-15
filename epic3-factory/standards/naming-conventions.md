# Naming Conventions — Kogito Migration

## Artefacts BPMN/DMN

| Artefact | Pattern | Exemple |
|----------|---------|---------|
| ID processus | `{domaine}-{nom}-v{N}` | `commerce-order-management-v2` |
| ID décision DMN | `{domaine}-{décision}-dmn` | `risk-credit-scoring-dmn` |
| ID tâche (Task) | `{verbe}-{objet}` | `validate-payment`, `notify-customer` |
| ID gateway | `{objet}-{question}-gateway` | `payment-approved-gateway` |
| ID event | `{type}-{déclencheur}` | `start-order-received`, `end-order-completed` |
| Nom fichier BPMN | `{id-processus}.bpmn` | `commerce-order-management-v2.bpmn` |
| Nom fichier DMN | `{id-décision}.dmn` | `risk-credit-scoring-dmn.dmn` |

## Variables de processus

| Règle | Exemple correct | Exemple incorrect |
|-------|----------------|------------------|
| camelCase | `orderId` | `order_id`, `OrderID` |
| Noms descriptifs | `customerEmail` | `email`, `e` |
| Boolean: préfixe is/has | `isPaymentValid` | `paymentValid`, `valid` |
| Listes: pluriel | `orderItems` | `item`, `itemList` |
| Jamais abbreviation opaque | `customerId` | `custId`, `cId` |

## Services Kubernetes et Java

| Élément | Pattern | Exemple |
|---------|---------|---------|
| Deployment K8s | `{domaine}-{service}` | `commerce-order-process` |
| Service K8s | idem | `commerce-order-process` |
| Package Java | `com.{company}.{domaine}.{module}` | `com.example.commerce.orders` |
| Classe WIH | `{Nom}WorkItemHandler` | `ValidatePaymentWorkItemHandler` |
| Classe Resource | `{Nom}Resource` | `OrderManagementResource` |
| Classe Worker | `{Nom}EventWorker` | `PaymentEventWorker` |

## Topics Kafka

| Pattern | Exemple |
|---------|---------|
| `kogito.{service}.{événement}` | `kogito.orders.payment-confirmed` |
| `{domaine}.{service}.domain` | `commerce.orders.domain` |
| `kogito-process-instances-domain` | (réservé Kogito) |
| `kogito-usertaskinstances-domain` | (réservé Kogito) |

## Versionning de processus

```
Règle: Un processus Kogito est identifié par son ID.
Changer l'ID = nouveau processus = nouvelle URL REST.

Version majeure (breaking change):
  commerce-order-management-v1 → commerce-order-management-v2
  → Coexistence des deux versions pendant la transition
  → Nouvelles instances → v2
  → Instances en cours → v1 jusqu'à complétion

Version mineure (compatible):
  → Modifier le BPMN sans changer l'ID
  → Redéployer le service
  → Kogito utilise la nouvelle définition pour les nouvelles instances
```

## Branches Git

| Branch | Usage |
|--------|-------|
| `main` | Production |
| `staging` | Pré-production |
| `feature/epic{N}-{description}` | Développement |
| `migration/{processus-id}` | Migration d'un processus spécifique |
| `hotfix/{description}` | Correctif urgent |
