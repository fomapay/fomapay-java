package cd.fomapay;

/** Aucune réponse : connexion impossible ({@code ERROR_NETWORK}) ou délai dépassé ({@code ERROR_TIMEOUT}). */
public class FomaPayNetworkException extends FomaPayException {
    public FomaPayNetworkException(String code, String message, Throwable cause) {
        super(code, message, 0, null, cause);
    }
}
