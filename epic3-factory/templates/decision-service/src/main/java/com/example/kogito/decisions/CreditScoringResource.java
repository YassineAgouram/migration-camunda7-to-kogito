package com.example.kogito.decisions;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kie.kogito.decision.DecisionModel;
import org.kie.kogito.decision.DecisionModels;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * CreditScoringResource
 *
 * API REST pour le service de décision DMN credit-scoring.
 * Kogito génère automatiquement /credit-scoring depuis le DMN,
 * ce fichier montre comment créer une API métier personnalisée.
 *
 * Remplace: decisionService.evaluateDecisionTable(...)  [Camunda DMN Engine]
 */
@Path("/api/decisions/credit")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CreditScoringResource {

    private static final Logger log = LoggerFactory.getLogger(CreditScoringResource.class);

    @Inject
    DecisionModels decisionModels;

    /**
     * Évaluer le scoring crédit.
     *
     * POST /api/decisions/credit/evaluate
     * Body: { "customerAge": 35, "monthlyIncome": 3000, "existingDebt": 500, "loanAmount": 12000 }
     * Response: { "creditScore": "GOOD", "loanApproval": "APPROVED", "debtRatio": 0.42 }
     */
    @POST
    @Path("/evaluate")
    @RolesAllowed({"kogito-user", "kogito-admin"})
    public Response evaluateCreditScore(CreditScoringRequest request) {
        log.info("Evaluating credit score for loan amount={}", request.loanAmount());

        try {
            // Récupérer le modèle DMN
            DecisionModel creditModel = decisionModels.getDecisionModel(
                    "http://www.example.com/kogito/dmn",
                    "Credit Scoring"
            );

            // Construire le contexte DMN
            DMNContext context = creditModel.newContext(Map.of(
                    "Customer Age", request.customerAge(),
                    "Monthly Income", request.monthlyIncome(),
                    "Existing Debt", request.existingDebt(),
                    "Loan Amount", request.loanAmount()
            ));

            // Évaluer toutes les décisions
            DMNResult result = creditModel.evaluateAll(context);

            if (result.hasErrors()) {
                log.error("DMN evaluation errors: {}", result.getMessages());
                return Response.serverError()
                        .entity(Map.of("error", "DMN evaluation failed",
                                "messages", result.getMessages().toString()))
                        .build();
            }

            // Extraire les résultats
            Map<String, Object> dmnResults = result.getContext().getAll();
            String creditScore = (String) dmnResults.get("Credit Score");
            String loanApproval = (String) dmnResults.get("Loan Approval");
            Number debtRatio = (Number) dmnResults.get("Debt Ratio");

            log.info("Credit evaluated: score={} approval={} ratio={}",
                    creditScore, loanApproval, debtRatio);

            return Response.ok(new CreditScoringResponse(
                    creditScore,
                    loanApproval,
                    debtRatio != null ? debtRatio.doubleValue() : 0.0
            )).build();

        } catch (Exception e) {
            log.error("Credit scoring failed", e);
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    // ─── DTOs ──────────────────────────────────────────────────

    public record CreditScoringRequest(
            int customerAge,
            double monthlyIncome,
            double existingDebt,
            double loanAmount
    ) {}

    public record CreditScoringResponse(
            String creditScore,       // EXCELLENT | GOOD | FAIR | POOR
            String loanApproval,      // APPROVED | MANUAL_REVIEW | REJECTED
            double debtRatio
    ) {}
}
