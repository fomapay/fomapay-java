package cd.fomapay;

/** Signature de webhook invalide ou expirée. */
public class FomaPaySignatureException extends FomaPayException {
    public FomaPaySignatureException(String code, String message) {
        super(code, message, 400, null, null);
    }
}
