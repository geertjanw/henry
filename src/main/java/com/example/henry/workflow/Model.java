package com.example.henry.workflow;

import java.util.List;

/** Serializable data passed through Temporal (Jackson handles records out of the box). */
public final class Model {

    private Model() {
    }

    public static final String TASK_QUEUE = "henry";

    /**
     * A unit of work. In the article this comes from Jira + Confluence; here it is posted directly.
     *
     * @param key         ticket key, also used for the branch name and code anchors, e.g. PROJ-123
     * @param repoUrl     anything {@code git clone} accepts (a remote URL or a local path)
     * @param baseBranch  branch to start from (null = the remote's default)
     * @param labels      behavior switches, e.g. "no-tests", "tech-review"
     * @param plan        an already reviewed implementation plan; if present the PLAN phase is skipped
     */
    public record DevTask(String key, String title, String description, String repoUrl,
                          String baseBranch, List<String> labels, String plan) {

        public boolean hasLabel(String label) {
            return labels != null && labels.contains(label);
        }
    }

    public record PhaseRequest(String workspacePath, DevTask task, Phase phase, String plan, String instruction) {
    }

    public record PhaseResult(Phase phase, String output, String finishReason, int iterations, int toolCalls,
                              long promptTokens, long completionTokens, double estimatedCostUsd) {
    }

    public record MergeRequest(String branch, String commitSha, String diffStat) {
    }

    public record ReviewComment(String threadId, String body) {
    }

    public record ReviewThread(String threadId, String comment, String reply, String commitSha) {
    }

    public record WorkflowStatus(String state, String workspacePath, String plan, String clarificationQuestions,
                                 String branch, String mergeRequestDescription, List<ReviewThread> threads,
                                 String error) {
    }

    /**
     * A one-line view of a workflow as Temporal's visibility store sees it — enough for the UI to list
     * what's running after a cold start, without the browser having to remember anything.
     *
     * @param key        ticket key (the workflow id with the "henry-" prefix stripped)
     * @param workflowId the full Temporal workflow id
     * @param status     Temporal's execution status: RUNNING, COMPLETED, FAILED, ...
     * @param startTime  ISO-8601 start instant (or null)
     * @param events     number of events in the workflow's history so far — the durable record Temporal
     *                   replays to rebuild state; it only ever grows while the workflow runs
     * @param state      the workflow's own phase label from its status() query (best-effort; may be null)
     */
    public record TaskSummary(String key, String workflowId, String status, String startTime, long events, String state) {
    }
}
