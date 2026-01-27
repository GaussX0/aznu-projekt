package org.aznu.languageschoolrest;

public class VerificationResult {
    private String applicationId;
    private boolean verified;
    private String reason;

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}