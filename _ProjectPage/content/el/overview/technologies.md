Κάθε έκδοση βιβλιοθήκης δηλώνεται μία φορά, σε έναν κοινό κατάλογο εκδόσεων του Gradle, ώστε όλα τα modules να χρησιμοποιούν την ίδια έκδοση κάθε βιβλιοθήκης.

## Tech stack

| Επίπεδο | Τεχνολογία | Έκδοση |
|---|---|---|
| Γλώσσα και κοινός κώδικας | Kotlin, Kotlin Multiplatform | 2.4.21 |
| Σειριοποίηση και ταυτοχρονισμός | kotlinx.serialization, kotlinx.coroutines | 1.11.0 |
| Σύστημα δόμησης | Gradle (Kotlin DSL), Android Gradle Plugin | 9.8.1, 9.1.0 |
| Backend | Ktor (server και client) | 3.6.0 |
| Πρόσβαση στη βάση | JetBrains Exposed, HikariCP, PostgreSQL JDBC driver | 1.5.1, 7.1.0, 42.7.14 |
| Βάση δεδομένων | PostgreSQL | 16 |
| Ασφάλεια | Ktor Auth JWT, BCrypt (at.favre), AES-GCM του JDK | 3.6.0, 0.10.2, – |
| Email | Jakarta Mail | 2.0.2 |
| Κωδικοί QR | ZXing, ZXing Android Embedded | 3.5.4, 4.3.0 |
| Web panel | Compose Multiplatform (Kotlin/Wasm) | 1.12.0 |
| Εφαρμογή kiosk | Jetpack Compose (BOM), Navigation Compose, DataStore, Security Crypto, Coil | 2026.06.01, 2.9.8, 1.2.1, 1.1.0, 2.7.0 |
| Πλατφόρμα Android | minSdk / compileSdk | 28 / 36 |
| Καταγραφή | Logback | 1.6.5 |
| Περιβάλλον εκτέλεσης | JDK 17 στην ανάπτυξη, JRE 21 στο Docker, nginx | – |

## Γιατί αυτές οι επιλογές

- **Μία γλώσσα.** Με Kotlin στον server, στον browser (μέσω WebAssembly) και στο Android, οι οντότητες και τα DTOs γράφονται μία φορά. Η αλλαγή ενός πεδίου προκαλεί σφάλμα μεταγλώττισης παντού ταυτόχρονα, αντί να αποτύχει κατά την εκτέλεση. Βλ. [Κεφάλαιο 4](/report/ch4).
- **Ktor.** Βασίζεται σε coroutines, δηλώνει τα routes σε απλή Kotlin και δεν χρησιμοποιεί reflection, γεγονός που διατηρεί σαφή τη ροή του αιτήματος. Η εργασία το συγκρίνει με το Spring Boot και το ASP.NET Core.
- **Exposed.** Οι πίνακες είναι αντικείμενα Kotlin, οπότε ο κώδικας είναι η μοναδική πηγή ορισμού του schema, και το `SET LOCAL search_path` ταιριάζει άμεσα στη δρομολόγηση schema-per-tenant.
- **PostgreSQL schemas.** Κάθε tenant έχει τους δικούς του πίνακες στην ίδια βάση, με ένα connection pool για όλους. Η απομόνωση εξαρτάται από τη σωστή επιλογή του schema σε κάθε συναλλαγή ([Αρχιτεκτονική](/overview/architecture)).
- **JWT με refresh-token rotation.** Stateless access tokens που ισχύουν 15 λεπτά· refresh tokens που ισχύουν 30 ημέρες, αποθηκεύονται μόνο ως SHA-256 hashes και αντικαθίστανται σε κάθε χρήση. Το kiosk χρησιμοποιεί ένα token συσκευής ενός έτους, το οποίο το backend ελέγχει σε κάθε αίτημα με βάση την εγγραφή της συσκευής, οπότε η απενεργοποίηση μιας συσκευής ισχύει από το επόμενο αίτημα.
- **AES-256-GCM για αποθηκευμένα μυστικά.** Ο κωδικός SMTP, το κλειδί του εταιρικού μητρώου και τα κλειδιά των πλατφορμών πρέπει να διαβάζονται ξανά σε καθαρή μορφή, γι' αυτό κρυπτογραφούνται με κλειδί που προκύπτει από μεταβλητή περιβάλλοντος και δεν αποθηκεύεται στη βάση.
- **Compose Multiplatform για το web panel.** Η διεπαφή του web panel γράφεται με τον ίδιο declarative τρόπο που χρησιμοποιεί και η εφαρμογή Android (Jetpack Compose), χωρίς JavaScript framework. Το τίμημα, ένα νεότερο οικοσύστημα, συζητείται στο [Κεφάλαιο 10](/report/ch10).

## Μέγεθος κώδικα

| Module | Αρχεία Kotlin | Γραμμές |
|---|---|---|
| `:domain` | 42 | 523 |
| `:data` | 46 | 637 |
| `:backend` | 79 | 5.695 |
| `:owner-web` | 46 | 9.450 |
| `:app` | 55 | 6.186 |
| **Σύνολο** | **268** | **22.491** |
