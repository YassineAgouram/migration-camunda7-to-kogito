package com.example.kogito.workers;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * PaymentEventWorker
 *
 * Worker réactif Kafka qui écoute les événements de paiement
 * et corrèle les réponses avec les instances de processus Kogito.
 *
 * Remplace le External Task Worker Camunda (Long Polling).
 *
 * Topics configurés dans application.properties :
 *   mp.messaging.incoming.payment-responses.*
 *   mp.messaging.outgoing.payment-confirmed.*
 */
@ApplicationScoped
public class PaymentEventWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventWorker.class);

    @Inject
    Process<OrderManagementModel> orderProcess;

    /**
     * Écoute les réponses de paiement depuis le Payment Service.
     * Corrèle avec l'instance de processus via processInstanceId.
     *
     * Pattern: Signal correlation (remplace External Task complete)
     */
    @Incoming("payment-responses")
    @Blocking
    public CompletionStage<Void> handlePaymentResponse(Message<PaymentResponseEvent> message) {
        PaymentResponseEvent event = message.getPayload();
        log.info("Received payment response processInstanceId={} status={}",
                event.processInstanceId(), event.status());

        try {
            Optional<ProcessInstance<OrderManagementModel>> instance =
                    orderProcess.instances().findById(event.processInstanceId());

            instance.ifPresentOrElse(
                    pi -> {
                        // Mettre à jour la variable paymentStatus
                        pi.updateVariables(vars -> {
                            vars.setPaymentStatus(event.status());
                            vars.setPaymentId(event.paymentId());
                        });

                        // Envoyer le signal pour reprendre le processus
                        pi.send(Sig.of("payment-confirmed", event.status()));
                        log.info("Payment correlated to processInstance={}", event.processInstanceId());
                    },
                    () -> log.warn("Process instance not found id={}", event.processInstanceId())
            );

            return message.ack();

        } catch (Exception e) {
            log.error("Failed to process payment response for instanceId={}",
                    event.processInstanceId(), e);
            return message.nack(e);
        }
    }

    /**
     * Écoute les demandes de remboursement (CloudEvent entrant)
     * et démarre un sous-processus de remboursement.
     */
    @Incoming("refund-requests")
    @Outgoing("refund-initiated")
    @Blocking
    public RefundInitiatedEvent handleRefundRequest(RefundRequestEvent request) {
        log.info("Processing refund request orderId={} amount={}", request.orderId(), request.amount());

        // Logique métier de validation du remboursement
        boolean approved = request.amount() <= 1000.0; // exemple simplifié

        return new RefundInitiatedEvent(
                request.orderId(),
                request.amount(),
                approved ? "APPROVED" : "REJECTED",
                System.currentTimeMillis()
        );
    }

    // ─── Event records (CloudEvents payload) ─────────────────

    public record PaymentResponseEvent(
            String processInstanceId,
            String paymentId,
            String status,         // CONFIRMED | FAILED | PENDING
            double amount,
            long timestamp
    ) {}

    public record RefundRequestEvent(
            String orderId,
            String customerId,
            double amount,
            String reason
    ) {}

    public record RefundInitiatedEvent(
            String orderId,
            double amount,
            String decision,
            long processedAt
    ) {}
}
