package com.schwab.urlshortener.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.schwab.urlshortener.observability.AuditRecorder;
import com.schwab.urlshortener.orchestration.persistence.ApprovalJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowEventEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowEventJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowInstanceEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowInstanceJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEngineStageBoundaryTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void crossStageWriteIsRejectedBeforeMutationAndAudited() {
        String workflowId = "stage-scope-" + java.util.UUID.randomUUID();
        persistInstance(workflowId);
        WorkflowStepEntity scoped = persistStep(workflowId, "scoped_stage", List.of(), 0);
        WorkflowStepEntity protectedStep = persistStep(workflowId, "protected_stage", List.of("scoped_stage"), 1);

        AtomicReference<StageScopedStepCapability> capability = new AtomicReference<>();
        StageHandler scopedHandler = context -> {
            capability.get().updateStatus(protectedStep.getId(), StepStatus.SUCCEEDED);
            context.addArtifact("unauthorizedWrite", true);
            return Map.of();
        };
        StageHandler protectedHandler = context -> Map.of("protected", true);
        StageCatalog catalog = new StageCatalog(
                Map.of("scoped_stage", scopedHandler, "protected_stage", protectedHandler), Map.of(), Map.of());
        capability.set(new StageScopedStepCapability(workflowId, "scoped_stage", scoped.getId(),
                stepRepository, new AuditRecorder(eventRepository)));

        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, transactionManager, null, null, null, null, catalog);
        engine.runFrom(workflowId);

        WorkflowStepEntity unchanged = stepRepository.findById(protectedStep.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(unchanged.getStartedAt()).isNull();
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getContextJson().toString())
                .doesNotContain("unauthorizedWrite");

        WorkflowEventEntity rejection = eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .filter(event -> event.getAction().equals("stage_scope_violation:update_step_status"))
                .findFirst().orElseThrow();
        assertThat(rejection.getActorType()).isEqualTo("agent");
        assertThat(rejection.getActorId()).isEqualTo("scoped_stage");
        assertThat(rejection.getResult()).isEqualTo("REJECTED");
        assertThat(rejection.getReason()).isNotBlank();
        assertThat(rejection.getAffectedArtifact()).isEqualTo(protectedStep.getId().toString());
    }

    private void persistInstance(String workflowId) {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(workflowId);
        instance.setFeature("stage-boundary");
        instance.setStatus(WorkflowStatus.RUNNING);
        instance.setCorrelationId(workflowId);
        instance.setPlanVersion(1);
        instance.setContextJson(Map.of("decisions", List.of(), "artifacts", Map.of()));
        instanceRepository.save(instance);
    }

    private WorkflowStepEntity persistStep(String workflowId, String name, List<String> dependencies, int sequence) {
        WorkflowStepEntity step = new WorkflowStepEntity();
        step.setWorkflowId(workflowId);
        step.setName(name);
        step.setStatus(StepStatus.PENDING);
        step.setSequence(sequence);
        step.setDependsOn(dependencies);
        step.setPlanVersion(1);
        step.setMaxRetries(0);
        step.setTimeoutSeconds(5);
        return stepRepository.save(step);
    }
}
