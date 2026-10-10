package cd.fomapay;

import java.util.Map;

/** L'API a répondu avec une erreur. */
public class FomaPayApiException extends FomaPayException {
    public FomaPayApiException(String code, String message, int status, Map<String, Object> details) {
        super(code, message, status, details, null);
    }
}
