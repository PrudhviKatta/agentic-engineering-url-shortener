package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StageHandlerContractTest {

    @Test
    void ingestionExtractsTheRequirementWithoutMutatingOrRetainingItsContainer() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("text", "Add expiresAt per REQ-014 and return HTTP 400 for past values");
        nested.put("submittedVia", "api");
        WorkflowContext context = contextWith(Map.of("requirement", nested));

        Map<String, Object> result = RequirementIngestionStage.run(context);
        nested.put("text", "mutated after ingestion");

        assertThat(result).containsEntry("ingested", true).containsEntry("submittedVia", "api");
        assertThat(context.artifacts()).containsEntry("requirementText",
                "Add expiresAt per REQ-014 and return HTTP 400 for past values");
        assertThat(nested).containsOnlyKeys("text", "submittedVia");
        assertThat(decisionNames(context)).containsExactly("requirement_ingested");
    }

    @Test
    void normalizationAndAmbiguityDetectionProduceADataDrivenClearDecision() {
        WorkflowContext context = contextWith(Map.of(
                "requirementText", "  Add EXPIRESAT   per REQ-014 and return HTTP 400  "));

        RequirementNormalizationStage.run(context);
        Map<String, Object> result = AmbiguityDetectionStage.run(context);

        assertThat(context.artifacts()).containsEntry("normalizedRequirement",
                "add expiresat per req-014 and return http 400");
        assertThat(result)
                .containsEntry("citesRequirement", true)
                .containsEntry("measurableCriterion", true)
                .containsEntry("ambiguityDetected", false);
        assertThat(context.artifacts()).containsEntry("ambiguityDetected", false);
        assertThat(decisionNames(context)).containsExactly("no_clarification_required");
    }

    @Test
    void aRequirementIdAloneDoesNotMasqueradeAsAMeasurableCriterion() {
        WorkflowContext context = contextWith(Map.of("normalizedRequirement",
                "improve request handling per req-014"));

        Map<String, Object> result = AmbiguityDetectionStage.run(context);

        assertThat(result)
                .containsEntry("citesRequirement", true)
                .containsEntry("measurableCriterion", false)
                .containsEntry("ambiguityDetected", true);
        assertThat(decisionNames(context)).containsExactly("clarification_required");
    }

    @Test
    void clarificationResolutionIsFoldedIntoDecompositionEvidence() {
        String resolution = "limit to 100 requests per minute per IP";
        WorkflowContext context = contextWith(Map.of("human_clarificationResolution", resolution));

        assertThat(HumanClarificationStage.run(context))
                .containsEntry("clarificationRecorded", true)
                .containsEntry("resolution", resolution);
        Map<String, Object> decomposition = DecompositionStage.run(context);

        assertThat(decomposition)
                .containsEntry("decomposed", true)
                .containsEntry("acceptanceCriteriaDefined", true)
                .containsEntry("clarifiedRequirement", resolution);
        assertThat(context.artifacts()).containsEntry("decomposition", decomposition);
        assertThat(decisionNames(context)).containsExactly("decomposition_used_clarification");
    }

    @Test
    void implementationVerificationAndDocumentationPublishTheirEvidenceContracts() {
        WorkflowContext context = new WorkflowContext("scenario-a");

        Map<String, Object> design = DesignStage.run(context);
        Map<String, Object> implementation = ImplementationStage.run(context);
        Map<String, Object> tests = TestingStage.run(context);
        Map<String, Object> security = SecurityStage.run(context);
        Map<String, Object> documentation = DocumentationStage.run(context);

        assertThat(design).containsEntry("acceptanceCriteriaMapped", true);
        assertThat(implementation)
                .containsEntry("urlSchemeAllowlistEnforced", true)
                .containsEntry("ssrfGuardEnforced", true);
        assertThat(tests).containsEntry("testsExecuted", true).containsEntry("testsPassed", true);
        assertThat(security).containsEntry("securityChecksPassed", true);
        assertThat(documentation).containsEntry("documentationComplete", true);
        assertThat(context.artifacts())
                .containsEntry("testsPassed", true)
                .containsEntry("securityChecksPassed", true)
                .containsEntry("documentationComplete", true);
    }

    @Test
    void testingSecurityAndDocumentationFailClosedWhenTheirInputsAreMissing() {
        WorkflowContext context = new WorkflowContext("scenario-a");

        assertThat(TestingStage.run(context)).containsEntry("testsPassed", false);
        assertThat(SecurityStage.run(context)).containsEntry("securityChecksPassed", false);
        assertThat(DocumentationStage.run(context)).containsEntry("documentationComplete", false);
    }

    @Test
    void readinessIsDerivedFromEvidenceAndDisclosesFallbackDegradation() {
        WorkflowContext context = readyContext(Map.of(
                "fallbacksUsed", new ArrayList<>(List.of("documentation"))));

        Map<String, Object> result = ReleaseReadinessStage.run(context);

        assertThat(result)
                .containsEntry("ready", true)
                .containsEntry("outcome", "READY_WITH_REDUCED_GUARANTEE")
                .containsEntry("degraded", true);
        assertThat(strings(result.get("degradedStages"))).containsExactly("documentation");
        assertThat(context.artifacts()).containsEntry("releaseReadiness", result);
        assertThat(decisionNames(context)).containsExactly("release_readiness_degraded");
    }

    @Test
    void readinessFailsClosedForMissingEvidenceAndUnapprovedPolicyState() {
        WorkflowContext context = contextWith(Map.of(
                "testsPassed", true,
                "securityChecksPassed", true,
                "documentationComplete", true,
                "mandatoryPolicyFailures", List.of("SEC-001"),
                "unapprovedPolicyExceptions", List.of("COMP-001")));

        Map<String, Object> result = ReleaseReadinessStage.run(context);

        assertThat(result).containsEntry("ready", false).containsEntry("outcome", "NOT_READY");
        assertThat(strings(result.get("reasons")))
                .contains("mandatory policy failures remain", "unapproved policy exceptions remain");
        assertThat(decisionNames(context)).containsExactly("release_not_ready");
    }

    @Test
    void brownfieldReadinessRequiresRecordedRollbackEvidence() {
        WorkflowContext missingPlan = readyContext(Map.of("brownfieldChange", true));
        assertThat(ReleaseReadinessStage.run(missingPlan)).containsEntry("ready", false);

        WorkflowContext withPlan = readyContext(Map.of(
                "brownfieldChange", true,
                "impact_rollout_rollback_finding", Map.of(
                        "rollbackPlan", "revert the isolated validation change")));
        assertThat(ReleaseReadinessStage.run(withPlan))
                .containsEntry("ready", true)
                .containsEntry("outcome", "READY");
    }

    @Test
    void finalSummaryIsDeterministicForTheSamePersistedEvidence() {
        Map<String, Object> artifacts = Map.of(
                "releaseReadiness", Map.of("ready", true, "outcome", "READY"),
                "testsPassed", true);
        WorkflowContext first = contextWith("run-42", artifacts);
        WorkflowContext second = contextWith("run-42", artifacts);
        first.recordDecision("human", "approved", "evidence reviewed");
        second.recordDecision("human", "approved", "evidence reviewed");

        Map<String, Object> firstResult = FinalSummaryStage.run(first);
        Map<String, Object> secondResult = FinalSummaryStage.run(second);

        assertThat(firstResult).isEqualTo(secondResult);
        assertThat(firstResult)
                .containsEntry("correlationId", "run-42")
                .containsEntry("decisionCount", 1);
        assertThat(strings(firstResult.get("artifactNames")))
                .containsExactly("releaseReadiness", "testsPassed");
        assertThat(first.artifacts()).containsEntry("finalEngineeringSummary", firstResult);
        assertThat(decisionNames(first)).endsWith("final_engineering_summary_recorded");
    }

    private static WorkflowContext readyContext(Map<String, Object> extras) {
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("testsPassed", true);
        artifacts.put("securityChecksPassed", true);
        artifacts.put("documentationComplete", true);
        artifacts.putAll(extras);
        return contextWith(artifacts);
    }

    private static WorkflowContext contextWith(Map<String, Object> artifacts) {
        return contextWith("scenario-a", artifacts);
    }

    private static WorkflowContext contextWith(String correlationId, Map<String, Object> artifacts) {
        return new WorkflowContext(correlationId, List.of(), artifacts);
    }

    private static List<String> decisionNames(WorkflowContext context) {
        return context.decisions().stream().map(decision -> String.valueOf(decision.get("decision"))).toList();
    }

    private static List<String> strings(Object value) {
        return ((List<?>) value).stream().map(String::valueOf).toList();
    }
}
