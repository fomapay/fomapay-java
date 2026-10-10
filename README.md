# fomapay-java

SDK Java officiel de **FomaPay** : encaissez en RDC par **FomaPay Direct** (QR / code marchand, dès le Niveau 0) et par **Mobile Money** (M-Pesa, Orange Money, Airtel Money, dès le Niveau 1).

- Java 17+, **aucune dépendance** (client HTTP du JDK)
- Builders pour les requêtes, exceptions typées
- Nouvelles tentatives automatiques et délai d'attente
- Vérification des webhooks (HMAC-SHA256)
- Compatible Spring Boot, Jakarta EE, Quarkus, ou Java sans framework

> SDK **serveur** : il utilise votre clé secrète `sk_…`, qui ne doit jamais être embarquée dans une application cliente.

## Intégration en 3 étapes

### 1. Installer

```xml
<dependency>
  <groupId>cd.fomapay</groupId>
  <artifactId>fomapay-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle : `implementation("cd.fomapay:fomapay-java:0.1.0")`

### 2. Créer un paiement

```java
FomaPay fomapay = new FomaPay("sk_test_…"); // FomaPay Business › Développeurs

Payment payment = fomapay.transactions().create(PaymentParams.builder()
    .amount(1000)
    .currency(Currency.CDF)
    .orderId("CMD-2026-001")
    .customer("Jean Marc", "+243810000000", "jean@example.com")
    .paymentModes(PaymentMode.FOMAPAY_DIRECT, PaymentMode.MOBILE_MONEY)
    .callbackUrl("https://marchand.cd/api/callback")
    .metadata("custom_field", "valeur")
    .build());

System.out.println(payment.getReference() + " " + payment.getQrCode()); // à afficher au client (FomaPay Direct)

// ou débiter directement son compte Mobile Money (push USSD)
fomapay.transactions().payWithMobileMoney(payment.getId(), Operator.AIRTEL_MONEY, "+243970000000");
```

### 3. Recevoir la confirmation (webhook)

```java
@PostMapping("/webhooks/fomapay")
ResponseEntity<Void> webhook(@RequestBody String payload, // le corps BRUT, tel que reçu
                             @RequestHeader(Webhook.SIGNATURE_HEADER) String signature) {
    try {
        Event event = Webhook.constructEvent(payload, signature, "whsec_…");
        if ("payment.succeeded".equals(event.getType())) {
            livrer(event.getData().getOrderId());
        }
        return ResponseEntity.ok().build();
    } catch (FomaPaySignatureException e) {
        return ResponseEntity.badRequest().build();
    }
}
```

`Webhook.verify(payload, signature, secret)` contrôle la signature sans décoder l'événement ; `Webhook.isValid(...)` renvoie un booléen.

## Référence

| Appel | Rôle |
| --- | --- |
| `fomapay.payments().create(params)` | Crée une demande de paiement |
| `fomapay.payments().retrieve(idOuReference)` | Relit un paiement |
| `fomapay.payments().list(limit, status)` | Derniers paiements de la boutique |
| `fomapay.payments().payWithMobileMoney(id, operator, phone)` | Push USSD vers le client |
| `fomapay.payments().simulate(id, "success" \| "failed")` | Force l'issue (sandbox uniquement) |
| `fomapay.payouts().create(PayoutParams.builder()…build())` | Retrait vers Mobile Money |
| `fomapay.balance().retrieve()` | Solde par devise |
| `fomapay.store().retrieve()` | Boutique, niveau KYC et modes autorisés |

`transactions()` est un alias de `payments()`. Les montants sont des `BigDecimal` en unités de la devise (`12.50` = 12,50 USD). Chaque modèle expose la réponse complète de l'API par `getRaw()`. Le client est sûr pour un usage concurrent : créez-en un seul.

## Erreurs

```java
try {
    fomapay.payments().create(params);
} catch (FomaPayApiException e) {        // l'API a répondu avec une erreur
    e.getCode();     // ex. ERROR_LEVEL_0_LIMIT_REACHED
    e.getStatus();   // statut HTTP
    e.getDetails();
} catch (FomaPayNetworkException e) {    // ERROR_NETWORK ou ERROR_TIMEOUT
}
```

`FomaPayApiException`, `FomaPayNetworkException` et `FomaPaySignatureException` héritent de `FomaPayException` (non vérifiée).

## Fiabilité

```java
FomaPay fomapay = FomaPay.builder("sk_live_…")
    .timeout(Duration.ofSeconds(10)) // 30 s par défaut
    .maxRetries(3)                   // 2 par défaut
    .build();
```

- **Nouvelles tentatives** avec attente exponentielle (0,5 s, 1 s… plafonnée à 8 s). Ne sont rejouées que les requêtes sans risque : les lectures, et les créations de paiement, qui portent toujours un en-tête `Idempotency-Key`. Un `429` est toujours rejoué en respectant `Retry-After`.
- **Idempotence** : `.idempotencyKey("CMD-2026-001")` pour qu'un appel répété par votre propre code renvoie le même paiement.
- **Retraits** : jamais rejoués automatiquement. Après une `FomaPayNetworkException`, vérifiez l'état avant de relancer.

## Sandbox

Avec une clé `sk_test_…`, aucun argent ne circule. Pour un push Mobile Money, le numéro décide de l'issue : terminé par `0001` → refusé, par `0002` → jamais validé, sinon confirmé après ~3 s.

## Tests

```bash
mvn test
```

Tests d'intégration contre le sandbox (ignorés si la variable est absente) :

```bash
FOMAPAY_API_URL=http://localhost:8080 FOMAPAY_TEST_SECRET_KEY=sk_test_… mvn test
```

Licence MIT.
