package com.example.henry.agent;

/**
 * The model endpoint rejected the request for a reason retrying won't fix: no credits (HTTP 402),
 * bad/missing API key (401), or rate limiting (429). The message is already user-facing — the
 * workflow surfaces it verbatim and marks the task failed instead of looping on the same error.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Classify a model-call failure. Returns a friendly, actionable message if this is a known
     * non-transient condition, or {@code null} if it looks like something worth retrying.
     */
    public static String diagnose(Throwable e) {
        String raw = deepestMessage(e);
        String msg = raw == null ? "" : raw.toLowerCase(java.util.Locale.ROOT);
        if (msg.contains("402") || msg.contains("more credits") || msg.contains("insufficient")
                || msg.contains("quota") || msg.contains("billing")) {
            return "Henry can't reach the model: the API returned HTTP 402 (no credits). "
                    + "Add credits at https://openrouter.ai/settings/credits, or set LLM_MODEL to a free "
                    + "model, then assign the ticket again.";
        }
        if (msg.contains("401") || msg.contains("unauthor") || msg.contains("api key") || msg.contains("api-key")
                || msg.contains("no auth credentials") || msg.contains("invalid key")) {
            return "The model API rejected Henry's credentials (HTTP 401). "
                    + "Check OPENROUTER_API_KEY (or OPENAI_API_KEY), then assign the ticket again.";
        }
        if (msg.contains("429") || msg.contains("rate limit") || msg.contains("rate-limit")) {
            return "The model API is rate-limiting Henry (HTTP 429). "
                    + "Wait a moment, or switch LLM_MODEL, then assign the ticket again.";
        }
        return null;
    }

    private static String deepestMessage(Throwable e) {
        Throwable c = e;
        String msg = e.getMessage();
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
            if (c.getMessage() != null) {
                msg = c.getMessage();
            }
        }
        return msg;
    }
}
