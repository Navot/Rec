package pkg;

public class PlanValidationResult {
    private boolean valid;
    private String reason;
    private String improvements;
    public PlanValidationResult(boolean valid, String reason, String improvements) {
        this.valid = valid;
        this.reason = reason;
        this.improvements = improvements;
    }

    public boolean isValid() {
        return valid;
    }

    public String getReason() {
        return reason;
    }

    public String getImprovements() {
        return improvements;
    }
}