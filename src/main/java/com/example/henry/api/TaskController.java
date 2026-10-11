package com.example.henry.api;

import com.example.henry.workflow.DevTaskWorkflow;
import com.example.henry.workflow.Model;
import com.example.henry.workflow.Model.DevTask;
import com.example.henry.workflow.Model.ReviewComment;
import com.example.henry.workflow.Model.TaskSummary;
import com.example.henry.workflow.Model.WorkflowStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionMetadata;
import io.temporal.client.WorkflowOptions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Stand-in for the Jira / GitLab / Slack webhooks of the real thing:
 * assigning a ticket starts a workflow, review comments and approvals become signals.
 */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private static final String ID_PREFIX = "henry-";

    private final WorkflowClient client;

    public TaskController(WorkflowClient client) {
        this.client = client;
    }

    @PostMapping
    public Map<String, String> assign(@RequestBody DevTask task) {
        DevTaskWorkflow wf = client.newWorkflowStub(DevTaskWorkflow.class, WorkflowOptions.newBuilder()
                .setTaskQueue(Model.TASK_QUEUE)
                .setWorkflowId(workflowId(task.key()))
                .build());
        var execution = WorkflowClient.start(wf::implement, task);
        return Map.of("workflowId", execution.getWorkflowId(), "runId", execution.getRunId());
    }

    /**
     * What is Henry working on? Answered entirely from Temporal's visibility store — so a freshly loaded
     * browser (or a brand-new one) can rebuild the list with no local state of its own. Each workflow's
     * live phase label is a best-effort status() query on top; it may be null if that query briefly fails.
     */
    @GetMapping
    public List<TaskSummary> list() {
        return client.listExecutions("WorkflowType = 'DevTaskWorkflow'")
                .sorted(Comparator.comparing(TaskController::startedAt).reversed())
                .map(this::summarize)
                .toList();
    }

    @GetMapping("/{key}")
    public WorkflowStatus status(@PathVariable String key) {
        return stub(key).status();
    }

    private TaskSummary summarize(WorkflowExecutionMetadata m) {
        String workflowId = m.getExecution().getWorkflowId();
        String key = workflowId.startsWith(ID_PREFIX) ? workflowId.substring(ID_PREFIX.length()) : workflowId;
        String state = null;
        try {
            state = stub(key).status().state();
        } catch (RuntimeException ignored) {
            // The workflow may have just closed, or the query may be momentarily unavailable; the
            // visibility status below is still accurate, so we simply omit the finer-grained phase.
        }
        String status = m.getStatus().name().replace("WORKFLOW_EXECUTION_STATUS_", "");
        Instant started = m.getStartTime();
        return new TaskSummary(key, workflowId, status, started == null ? null : started.toString(),
                m.getHistoryLength(), state);
    }

    private static Instant startedAt(WorkflowExecutionMetadata m) {
        Instant t = m.getStartTime();
        return t == null ? Instant.EPOCH : t;
    }

    @PostMapping("/{key}/clarification")
    public void clarify(@PathVariable String key, @RequestBody Map<String, String> body) {
        stub(key).answerClarification(body.get("answer"));
    }

    @PostMapping("/{key}/comments")
    public void comment(@PathVariable String key, @RequestBody ReviewComment comment) {
        stub(key).reviewComment(comment);
    }

    @PostMapping("/{key}/approve")
    public void approve(@PathVariable String key) {
        stub(key).approve();
    }

    /**
     * Terminate a workflow — Temporal's hard stop: it ends immediately, no cleanup, and is recorded as the
     * final event in its history (contrast with cancel, which asks the workflow to wind down gracefully).
     * Useful here for clearing out a stuck or finished run.
     */
    @DeleteMapping("/{key}")
    public void terminate(@PathVariable String key) {
        client.newUntypedWorkflowStub(workflowId(key)).terminate("Terminated from the Henry UI");
    }

    private DevTaskWorkflow stub(String key) {
        return client.newWorkflowStub(DevTaskWorkflow.class, workflowId(key));
    }

    private static String workflowId(String key) {
        return ID_PREFIX + key;
    }
}
