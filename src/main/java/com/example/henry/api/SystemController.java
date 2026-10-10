package com.example.henry.api;

import com.example.henry.workflow.Model;
import io.temporal.api.workflowservice.v1.CountWorkflowExecutionsRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Lets the browser UI drive the demo without a terminal.
 * <ul>
 *   <li>Stop / Continue suspend and resume the Temporal worker so Henry stops / resumes
 *       picking up tasks. The web server stays up so the page stays reachable.</li>
 *   <li>Restart Temporal kills whatever is on the dev-server port and relaunches a fresh
 *       {@code temporal server start-dev}. The dev server is in-memory, so this wipes history.</li>
 * </ul>
 * The real thing wouldn't expose any of this.
 */
@RestController
public class SystemController {

    private static final int TEMPORAL_PORT = 7233;

    private final WorkerFactory workerFactory;
    private final WorkflowClient client;

    public SystemController(WorkerFactory workerFactory, WorkflowClient client) {
        this.workerFactory = workerFactory;
        this.client = client;
    }

    private Worker worker() {
        return workerFactory.getWorker(Model.TASK_QUEUE);
    }

    @GetMapping("/worker")
    public Map<String, Object> state() {
        // hasHistory tells the UI whether resuming would "Continue" existing work or just "Start":
        // a freshly restarted (in-memory) Temporal has no executions, so nothing to continue.
        return Map.of("paused", worker().isSuspended(), "hasHistory", hasHistory());
    }

    private boolean hasHistory() {
        try {
            String ns = client.getOptions().getNamespace();
            long count = client.getWorkflowServiceStubs().blockingStub().countWorkflowExecutions(
                    CountWorkflowExecutionsRequest.newBuilder().setNamespace(ns).build()).getCount();
            return count > 0;
        } catch (Exception e) {
            // Temporal unreachable (e.g. mid-restart) → treat as no history.
            return false;
        }
    }

    @PostMapping("/pause")
    public Map<String, Object> pause() {
        worker().suspendPolling();
        return Map.of("paused", true);
    }

    @PostMapping("/resume")
    public Map<String, Object> resume() {
        worker().resumePolling();
        return Map.of("paused", false);
    }

    @GetMapping("/temporal")
    public Map<String, Object> temporalState() {
        return Map.of("up", portOpen(TEMPORAL_PORT));
    }

    @PostMapping("/temporal/restart")
    public Map<String, Object> restartTemporal() {
        String bin = findTemporalBinary();
        if (bin == null) {
            return Map.of("status", "error",
                    "message", "Could not find the 'temporal' CLI. Install it or start the dev server manually.");
        }
        // Kill only the LISTENING process (the server) — not client sockets, or we would kill
        // this very app, which also has a connection to the port. Then relaunch detached.
        String script = "lsof -ti :" + TEMPORAL_PORT + " -sTCP:LISTEN | xargs kill -9 >/dev/null 2>&1; sleep 1; "
                + "nohup '" + bin + "' server start-dev >/tmp/temporal-dev.log 2>&1 &";
        try {
            new ProcessBuilder("/bin/bash", "-c", script)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return Map.of("status", "restarting");
        } catch (IOException e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    private static boolean portOpen(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String findTemporalBinary() {
        // Prefer whatever the user's own shell resolves (matches `which temporal`).
        try {
            Process p = new ProcessBuilder("/bin/bash", "-lc", "command -v temporal").start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            p.waitFor();
            if (!out.isBlank() && Files.isExecutable(Path.of(out))) {
                return out;
            }
        } catch (IOException e) {
            // ignore and try known locations
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Fall back to common install locations.
        List<String> candidates = List.of(
                "/opt/homebrew/bin/temporal",
                "/usr/local/bin/temporal",
                System.getProperty("user.home") + "/.temporalio/bin/temporal");
        for (String c : candidates) {
            if (Files.isExecutable(Path.of(c))) {
                return c;
            }
        }
        return null;
    }
}
