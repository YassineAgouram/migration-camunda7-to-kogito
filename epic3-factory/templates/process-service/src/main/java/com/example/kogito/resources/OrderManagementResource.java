package com.example.kogito.resources;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceReadMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OrderManagementResource
 *
 * API REST pour le processus order-management-v1.
 * Kogito génère automatiquement les endpoints REST à partir du BPMN,
 * mais ce fichier montre comment les enrichir ou les personnaliser.
 *
 * Note: Les endpoints générés automatiquement par Kogito sont disponibles
 * sous /order-management-v1 — ce fichier illustre une couche additionnelle.
 */
@Path("/api/orders")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Order Management", description = "API de gestion des commandes")
public class OrderManagementResource {

    private static final Logger log = LoggerFactory.getLogger(OrderManagementResource.class);

    // Kogito génère et injecte automatiquement le Process à partir du BPMN
    @Inject
    Process<OrderManagementModel> orderProcess;

    /**
     * Démarrer une nouvelle instance de processus commande.
     * Remplace: runtimeService.startProcessInstanceByKey("order-management", variables)
     */
    @POST
    @Operation(summary = "Créer une nouvelle commande")
    @RolesAllowed({"process-starter", "kogito-admin"})
    public Response startOrder(StartOrderRequest request) {
        log.info("Starting order process for customerId={}", request.customerId());

        OrderManagementModel model = new OrderManagementModel();
        model.setOrderId(request.orderId());
        model.setCustomerId(request.customerId());
        model.setItems(request.items());
        model.setCustomerEmail(request.customerEmail());

        ProcessInstance<OrderManagementModel> instance = orderProcess.createInstance(model);
        instance.start();

        log.info("Order process started instanceId={} orderId={}", instance.id(), request.orderId());

        return Response
                .created(URI.create("/api/orders/" + instance.id()))
                .entity(Map.of(
                        "processInstanceId", instance.id(),
                        "orderId", request.orderId(),
                        "status", instance.status()
                ))
                .build();
    }

    /**
     * Récupérer le statut d'une instance de processus.
     * Remplace: historyService.createHistoricProcessInstanceQuery()...
     */
    @GET
    @Path("/{processInstanceId}")
    @Operation(summary = "Statut d'une commande")
    @RolesAllowed({"kogito-user", "kogito-admin", "kogito-readonly"})
    public Response getOrderStatus(@PathParam("processInstanceId") String processInstanceId) {
        Optional<ProcessInstance<OrderManagementModel>> instance =
                orderProcess.instances().findById(processInstanceId, ProcessInstanceReadMode.READ_ONLY);

        return instance
                .map(pi -> Response.ok(Map.of(
                        "processInstanceId", pi.id(),
                        "status", pi.status(),
                        "orderId", pi.variables().getOrderId(),
                        "paymentStatus", pi.variables().getPaymentStatus()
                )).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Lister toutes les instances actives.
     */
    @GET
    @Operation(summary = "Lister les commandes actives")
    @RolesAllowed({"kogito-admin", "kogito-readonly"})
    public List<Map<String, Object>> listActiveOrders() {
        return orderProcess.instances()
                .values(ProcessInstanceReadMode.READ_ONLY)
                .stream()
                .map(pi -> Map.<String, Object>of(
                        "processInstanceId", pi.id(),
                        "orderId", pi.variables().getOrderId(),
                        "customerId", pi.variables().getCustomerId(),
                        "status", pi.status()
                ))
                .toList();
    }

    /**
     * Annuler une instance de processus.
     * Remplace: runtimeService.deleteProcessInstance(id, reason)
     */
    @DELETE
    @Path("/{processInstanceId}")
    @Operation(summary = "Annuler une commande")
    @RolesAllowed({"kogito-admin"})
    public Response cancelOrder(@PathParam("processInstanceId") String processInstanceId) {
        Optional<ProcessInstance<OrderManagementModel>> instance =
                orderProcess.instances().findById(processInstanceId);

        return instance.map(pi -> {
            pi.abort();
            log.info("Order process aborted instanceId={}", processInstanceId);
            return Response.noContent().build();
        }).orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    // ─── DTOs ──────────────────────────────────────────────────

    public record StartOrderRequest(
            String orderId,
            String customerId,
            String customerEmail,
            List<OrderItem> items
    ) {}

    public record OrderItem(String productId, int qty, double price) {}
}
