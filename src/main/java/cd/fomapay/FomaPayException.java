package cd.fomapay;

import java.util.Map;

/**
 * Erreur de base du SDK. {@link #getCode()} est stable et exploitable par le programme
 * (ex. {@code ERROR_LEVEL_0_LIMIT_REACHED}) ; {@link #getMessage()} est lisible par un humain.
 */
public class FomaPayException extends RuntimeException {
    private final String code;
    private final int status;
    private final transient Map<String, Object> details;

    public FomaPayException(String code, String message, int status, Map<String, Object> details, Throwable cause) {
        super(code + ": " + message, cause);
        this.code = code;
        this.status = status;
        this.details = details == null ? Map.of() : details;
    }

    public FomaPayException(String code, String message) {
        this(code, message, 0, null, null);
    }

    public String getCode() {
        return code;
    }

    /** Statut HTTP (0 quand il n'y a pas eu de réponse). */
    public int getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
