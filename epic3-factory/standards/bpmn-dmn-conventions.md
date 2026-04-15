# Conventions de Modélisation BPMN/DMN — Kogito Cible

## Conventions de nommage

### Processus BPMN
```
Format ID  : {domaine}-{nom-processus}-{version}
Exemple    : commerce-order-management-v2

Format Name: [Domaine] Nom du processus
Exemple    : [Commerce] Gestion des commandes
```

### Événements et activités
| Élément | Convention | Exemple |
|---------|------------|---------|
| Start Event | `start-{déclencheur}` | `start-order-received` |
| End Event | `end-{résultat}` | `end-order-completed` |
| Task | Verbe + Nom | `validate-payment` |
| Gateway | Question? | `payment-approved?` |
| Subprocess | `sub-{nom}` | `sub-fraud-check` |

### Variables de processus
```
Format     : camelCase
Exemples   : orderId, customerId, paymentStatus
Interdit   : order_id, OrderID, PAYMENT_STATUS
```

---

## Patterns de remplacement des extensions Camunda

### 1. Java Delegate → CDI Service Bean

**❌ Camunda 7 (Java Delegate)**
```xml
<serviceTask id="validatePayment"
             camunda:delegateExpression="${validatePaymentDelegate}" />
```
```java
@Component
public class ValidatePaymentDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        String orderId = (String) execution.getVariable("orderId");
        // logique métier
    }
}
```

**✅ Kogito (Work Item Handler ou REST Task)**
```xml
<!-- Option 1: Custom Work Item -->
<serviceTask id="validatePayment" name="Validate Payment">
  <extensionElements>
    <kogito:workItemDefinition name="ValidatePayment"/>
  </extensionElements>
</serviceTask>

<!-- Option 2: REST Task (recommandé pour appels HTTP) -->
<serviceTask id="validatePayment" name="Validate Payment">
  <extensionElements>
    <kogito:workItemDefinition name="Rest"/>
    <kogito:workItemParameter name="Url" value="http://payment-service/validate"/>
    <kogito:workItemParameter name="Method" value="POST"/>
  </extensionElements>
</serviceTask>
```

---

### 2. Script JSR-223 → Expression MVEL ou Service Bean

**❌ Camunda 7 (Script Groovy)**
```xml
<scriptTask id="calculateTotal" scriptFormat="groovy">
  <script>
    def total = items.collect { it.price * it.qty }.sum()
    execution.setVariable("total", total)
  </script>
</scriptTask>
```

**✅ Kogito (Expression MVEL)**
```xml
<scriptTask id="calculateTotal" scriptFormat="http://www.mvel.org/2.0">
  <script>total = items.stream().mapToDouble(i -> i.price * i.qty).sum();</script>
</scriptTask>
```

Ou mieux : externaliser dans un service:
```xml
<serviceTask id="calculateTotal">
  <extensionElements>
    <kogito:workItemDefinition name="CalculateOrderTotal"/>
  </extensionElements>
</serviceTask>
```

---

### 3. External Task → Message Event / Service Task avec CDI

**❌ Camunda 7 (External Task Worker)**
```java
externalTaskService.fetchAndLock(10, "worker-id")
    .topic("process-payment", 60_000L)
    .execute()
    .forEach(task -> {
        // traitement
        externalTaskService.complete(task.getId(), "worker-id", variables);
    });
```

**✅ Kogito (Réactif Kafka)**
```java
@ApplicationScoped
public class PaymentWorker {

    @Inject
    KogitoProcessRuntime runtime;

    @Incoming("payment-request")
    @Outgoing("payment-response")
    public PaymentResponse processPayment(PaymentRequest request) {
        // traitement
        return new PaymentResponse(request.getProcessInstanceId(), status);
    }
}
```

---

### 4. Timers Camunda → Timers BPMN standard (Jobs Service)

**❌ Camunda 7**
```xml
<intermediateCatchEvent>
  <timerEventDefinition>
    <timeDuration>PT30M</timeDuration>
  </timerEventDefinition>
</intermediateCatchEvent>
```

**✅ Kogito (identique syntaxe + Jobs Service requis)**
```xml
<!-- Identique, mais Jobs Service DOIT être déployé -->
<intermediateCatchEvent id="wait30min">
  <timerEventDefinition>
    <timeDuration xsi:type="tFormalExpression">PT30M</timeDuration>
  </timerEventDefinition>
</intermediateCatchEvent>
```

---

## Conventions d'erreurs BPMN

```xml
<!-- Définition d'erreur standard -->
<error id="PaymentError" name="Payment Failed" errorCode="PAYMENT_FAILED"/>
<error id="ValidationError" name="Validation Error" errorCode="VALIDATION_ERROR"/>

<!-- Format des errorCode : DOMAINE_ACTION_ÉTAT en SCREAMING_SNAKE_CASE -->
<!-- Exemples : ORDER_CREATION_FAILED, PAYMENT_TIMEOUT, FRAUD_DETECTED -->
```

---

## Conventions de versionning

| Règle | Exemple |
|-------|---------|
| Version dans l'ID du processus | `order-management-v2` |
| Changement majeur → incrément version | v1 → v2 |
| Changement mineur compatible → pas de nouveau ID | patch interne |
| Anciennes versions conservées jusqu'à 0 instance active | — |

---

## Conventions d'événements et corrélation

```xml
<!-- Signal : communication broadcast inter-processus -->
<signal id="OrderCancelledSignal" name="order-cancelled-{orderId}"/>

<!-- Message : communication point-à-point -->
<message id="PaymentConfirmedMessage" name="payment-confirmed"/>

<!-- Format des noms de corrélation -->
<!-- signal : {domaine}-{événement}-{clé de corrélation} -->
<!-- message : {domaine}-{événement} -->
```
