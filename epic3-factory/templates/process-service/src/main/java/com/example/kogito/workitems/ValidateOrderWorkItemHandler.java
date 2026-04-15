package com.example.kogito.workitems;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kie.kogito.internal.process.runtime.KogitoWorkItem;
import org.kie.kogito.internal.process.runtime.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.runtime.KogitoWorkItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ValidateOrderWorkItemHandler
 *
 * Remplace le Java Delegate Camunda ValidateOrderDelegate.
 * Enregistré automatiquement via CDI (@ApplicationScoped).
 *
 * Référencé dans le BPMN via :
 *   <kogito:workItemDefinition name="ValidateOrder"/>
 */
@ApplicationScoped
public class ValidateOrderWorkItemHandler implements KogitoWorkItemHandler {

    private static final Logger log = LoggerFactory.getLogger(ValidateOrderWorkItemHandler.class);

    @Inject
    OrderValidationService validationService;

    /**
     * Appelé à l'exécution de la Service Task BPMN.
     */
    @Override
    public void executeWorkItem(KogitoWorkItem workItem, KogitoWorkItemManager manager) {
        String orderId = (String) workItem.getParameter("orderId");
        List<?> items = (List<?>) workItem.getParameter("items");

        log.info("Validating order orderId={} itemCount={}", orderId, items != null ? items.size() : 0);

        try {
            ValidationResult result = validationService.validate(orderId, items);

            Map<String, Object> results = new HashMap<>();
            results.put("validationErrors", result.getErrors());
            results.put("totalAmount", result.getTotalAmount());

            // Complétion normale du Work Item
            manager.completeWorkItem(workItem.getStringId(), results);

        } catch (Exception e) {
            log.error("Validation failed for orderId={}", orderId, e);
            // Lever une erreur BPMN pour déclencher le Boundary Error Event
            manager.abortWorkItem(workItem.getStringId());
        }
    }

    /**
     * Appelé si le processus est annulé pendant l'exécution.
     */
    @Override
    public void abortWorkItem(KogitoWorkItem workItem, KogitoWorkItemManager manager) {
        log.warn("Order validation aborted for orderId={}", workItem.getParameter("orderId"));
        manager.abortWorkItem(workItem.getStringId());
    }

    @Override
    public String getName() {
        return "ValidateOrder";
    }
}
