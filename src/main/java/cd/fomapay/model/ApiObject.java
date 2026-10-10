package cd.fomapay.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Base des modèles : chacun enveloppe la réponse JSON de l'API, accessible par {@link #getRaw()}.
 * Un champ ajouté par l'API reste donc lisible sans mise à jour du SDK.
 */
public abstract class ApiObject {
    private final Map<String, Object> raw;

    protected ApiObject(Map<String, Object> raw) {
        this.raw = raw == null ? Map.of() : Collections.unmodifiableMap(raw);
    }

    /** Réponse JSON complète de l'API. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    protected String string(String key) {
        Object value = raw.get(key);
        return value == null ? null : value.toString();
    }

    protected BigDecimal decimal(String key) {
        return raw.get(key) instanceof BigDecimal d ? d : BigDecimal.ZERO;
    }

    protected boolean bool(String key) {
        return Boolean.TRUE.equals(raw.get(key));
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> map(String key) {
        return raw.get(key) instanceof Map<?, ?> m ? Collections.unmodifiableMap((Map<String, Object>) m) : null;
    }

    protected List<String> strings(String key) {
        return raw.get(key) instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + raw;
    }
}
