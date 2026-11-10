## Glossary of Foreign Terms and Acronyms

Table B.1 brings together the foreign terms used in the text, with their Greek rendering and a short explanation. Table B.2 brings together the acronyms, with their full form and a short explanation.

### B.1 Foreign terms

**Table B.1: Foreign terms, Greek rendering and explanation**

| Term | Greek Rendering | Description |
|---|---|---|
| Access token | Διακριτικό πρόσβασης | Short-lived token sent with every request to prove the user's identity. |
| Acquirer | Φορέας πληρωμών (αποδέκτης) | The provider that processes and approves card payments on behalf of the merchant. |
| Adapter | Προσαρμογέας | Implementation of a port for a specific technology or external service. |
| Audit log | Αρχείο καταγραφής ενεργειών | Chronological record of user actions, for review and accountability. |
| Backend | Σύστημα παρασκηνίου | Server logic, databases and the processing of business rules. |
| Browser | Πρόγραμμα περιήγησης | The application used to open web pages, here also the web panel. |
| Brute force | Εξαντλητική δοκιμή | Attack that tries many values in turn, e.g. passwords, until one works. |
| Build | Δόμηση | The process that produces runnable files from the source code. |
| Bytecode | Ενδιάμεσος κώδικας | Code for a virtual machine (e.g. the JVM) rather than for the processor. |
| Clean Architecture | Καθαρή Αρχιτεκτονική | Architectural pattern of concentric layers with a dependency rule pointing toward the centre. |
| Client | Πελάτης (λογισμικό) | Program that sends requests to a server, here the web panel and the kiosk app. |
| Compare-and-set | Σύγκριση και ενημέρωση | Update applied only if the current value is the expected one, so two concurrent changes cannot overwrite each other. |
| Compiler | Μεταγλωττιστής | Program that turns source code into a runnable form and checks the types. |
| Composable | Συνάρτηση διεπαφής (Compose) | Function that describes a part of the interface in Jetpack Compose and Compose Multiplatform. |
| Connection pool | Δεξαμενή συνδέσεων | Set of open database connections that requests reuse. |
| Container | Περιέκτης | Isolated environment that runs an application together with its dependencies (Docker). |
| Coroutine | Συρρουτίνα | Lightweight unit of asynchronous work that suspends without blocking a thread. |
| Cross-platform | Διαπλατφορμικός | Software or code that runs on more than one platform. |
| Dashboard | Πίνακας ελέγχου | Screen that summarises the key figures. |
| Declarative UI | Δηλωτική διεπαφή | The interface is described as a function of the state, and the framework updates the screen when the state changes. |
| Dependency injection | Έγχυση εξαρτήσεων | An object receives its dependencies from outside instead of creating them itself. |
| Device Owner | Ιδιοκτήτης συσκευής (Android) | Special Android management privilege that gives full control of the device, e.g. for kiosk mode. |
| Domain | Πεδίο εφαρμογής | The concepts and rules of the problem the system solves, independent of technology. |
| Emulated adapter | Προσομοιωμένος προσαρμογέας | Implementation of an external service that reproduces its functionality locally. |
| Emulator | Εξομοιωτής | Software that reproduces a device, e.g. an Android tablet, on the computer. |
| Endpoint | Σημείο πρόσβασης | The combination of HTTP method and path that the API answers. |
| Enum | Απαριθμητός τύπος | Type with a fixed set of values, e.g. the statuses of an order. |
| Epoch milliseconds | Χιλιοστά δευτερολέπτου από την εποχή Unix | Timestamp as the number of milliseconds since 1 January 1970 (UTC). |
| Firewall | Τείχος προστασίας | Software or device that allows or blocks network connections by rules. |
| Foreign key | Ξένο κλειδί | Table column that refers to a row of another table and keeps the data consistent. |
| Framework | Πλαίσιο ανάπτυξης | Ready-made software infrastructure on which an application is built. |
| Frontend | Σύστημα προσκηνίου | The part of the application the end user interacts with directly. |
| Garbage collection | Συλλογή απορριμμάτων | Automatic release of memory that is no longer used. |
| Hash | Σύνοψη κατακερματισμού | One-way transformation of data into a fixed-length value; used to store passwords and tokens. |
| Health check | Έλεγχος λειτουργίας | Endpoint or command that confirms a service is working. |
| Heartbeat (Ping) | Παλμός λειτουργίας | Periodic signal that checks a device's availability and active connection. |
| Hexagonal Architecture | Εξαγωνική Αρχιτεκτονική | Ports and Adapters pattern that isolates the core from external dependencies. |
| Idempotent | Ταυτοδύναμος | Operation that does not change the result when run again. |
| Kiosk | Σημείο αυτοεξυπηρέτησης | Touch-screen device locked to one application, for use by the public. |
| Kiosk mode | Λειτουργία περιπτέρου | Locking a device to a single full-screen application with no access to the OS. |
| LockTask mode | Λειτουργία κλειδώματος εργασίας | Android's native mechanism for locking the device to one application. |
| Log | Αρχείο καταγραφής | The messages an application records while it runs, used to diagnose problems. |
| Loopback | Διεπαφή επιστροφής | The computer's own network address (localhost), not reachable from other devices. |
| Middleware | Ενδιάμεσο λογισμικό | Processing stage every request passes through before it reaches the code that serves it. |
| Migration | Μετάπτωση σχήματος | Controlled change of the database structure from one version to the next. |
| Module | Λειτουργική μονάδα | Self-contained part of the project with its own code and dependencies. |
| Monorepo | Ενιαίο αποθετήριο | Strategy of storing multiple related sub-projects in one shared repository. |
| Multi-tenancy | Πολυμίσθωση | Architecture serving multiple independent customers from a single infrastructure. |
| Nullable | Με δυνατότητα κενής τιμής | Field or type that allows the value null, i.e. no value. |
| Onboarding | Ένταξη | Registering and setting up a new owner on the platform. |
| Plaintext | Μη κρυπτογραφημένο κείμενο | Data sent or stored without encryption. |
| Plugin | Πρόσθετο | Self-contained part that adds a feature to a framework or tool. |
| Polling | Περιοδική ερώτηση | The client sends requests to the server at regular intervals to check whether anything has changed. |
| Port | Θύρα | In the architecture: a domain interface that states what the core needs from the outside world. In networking: the number a service listens on. |
| Race condition | Συνθήκη ανταγωνισμού | Fault that appears when the result depends on the order of concurrent operations. |
| Rate limit (rate limiting) | Όριο ρυθμού αιτημάτων | Limit on how many requests the server accepts per time period, e.g. 20 per minute; exceeding it is answered with 429 Too Many Requests. |
| Reflection | Ανάκλαση | Inspecting and using classes and methods while the program runs. |
| Refresh token | Διακριτικό ανανέωσης | Long-lived token used only to obtain a new access token. |
| Refresh token rotation | Κυκλική εναλλαγή διακριτικών | Security mechanism in which each refresh issues a new, unique refresh token. |
| Replay attack | Επίθεση επανάληψης | Attack that reuses a valid message or token that was intercepted. |
| Repository pattern | Πρότυπο αποθετηρίου | Design pattern that mediates between the business layer and data sources. |
| Reverse proxy | Αντίστροφος διαμεσολαβητής | Server that receives client requests and forwards them to internal services (here nginx). |
| Route | Διαδρομή | The API path that a piece of code is mapped to. |
| Runtime | Περιβάλλον εκτέλεσης | The environment, and the phase, in which a program runs. |
| Schema | Σχήμα βάσης | Namespace inside a PostgreSQL database with its own tables. |
| Schema-per-tenant | Σχήμα ανά μισθωτή | Isolation model in which each customer has a separate schema in the same database. |
| Screen pinning | Καρφίτσωμα οθόνης | Android feature that keeps one app on screen until the user unpins it. |
| Script | Σενάριο εντολών | File of commands that run automatically in order. |
| Seed data | Αρχικά δεδομένα | Demo data loaded into the database to try out the system. |
| Self check-in | Αυτόνομη άφιξη | The guest enters the property without meeting the owner. |
| Server | Διακομιστής | Computer or program that receives and serves requests from clients. |
| Server-Sent Events | Συμβάντα από τον διακομιστή | Mechanism by which the server pushes updates to the browser over an open HTTP connection. |
| Session | Συνεδρία | The period during which a user stays signed in. |
| Shared schema | Κοινό σχήμα | Model in which all tenants share the same tables and are told apart by an identifier column. |
| Soft delete | Λογική διαγραφή | Marking a record as inactive without physically deleting it from the table. |
| Source set | Σύνολο πηγαίου κώδικα | Code folder of a module for a specific platform, e.g. commonMain or jvmMain. |
| State machine | Μηχανή καταστάσεων | Computational model that controls the permitted order status transitions. |
| Stateless | Χωρίς κατάσταση | Architecture in which each request carries all the information needed to execute it. |
| Tablet | Ταμπλέτα | Portable touch-screen device; here the kiosk device inside the property. |
| Tech stack | Τεχνολογίες του έργου | The languages, libraries and tools a project uses. |
| Tenant | Μισθωτής | Customer of the platform with its own isolated data; here each owner. |
| Token | Διακριτικό ασφαλείας | Cryptographic symbol that certifies a user's identity and permissions. |
| Trade-off | Αντιστάθμισμα | The cost or limitation that comes with a design choice. |
| Transaction | Συναλλαγή (βάσης δεδομένων) | Set of database operations that are applied all together or not at all. |
| Type safety | Ασφάλεια τύπων | The compiler catches type mismatches before the program runs. |
| Use case | Περίπτωση χρήσης | Description of how a user interacts with the system to reach a goal. |
| ViewModel | Μοντέλο προβολής | Class that holds the state of a screen separately from the interface. |
| Volume | Τόμος δεδομένων (Docker) | Persistent storage that remains when the container is removed. |
| Web panel | Διαδικτυακός πίνακας διαχείρισης | The web application administrators and owners use to manage the system. |
| Webhook | Ειδοποίηση μέσω HTTP | HTTP request that an external service sends when an event happens. |
| WebSocket | Αμφίδρομη σύνδεση web | Protocol for a persistent two-way connection between browser and server. |

### B.2 Acronyms

**Table B.2: Acronyms, full form and explanation**

| Acronym | Full Form | Description |
|---|---|---|
| AES | Advanced Encryption Standard | Symmetric encryption standard; here for stored secrets. |
| AI | Artificial Intelligence | Artificial intelligence. |
| AIDL | Android Interface Definition Language | Interface definition language for communication between apps and services on Android. |
| API | Application Programming Interface | Application programming interface; the set of endpoints the backend exposes. |
| APK | Android Package | The installation file of an Android app. |
| BOM | Bill of Materials | List of compatible versions for a group of libraries. |
| CLI | Command-Line Interface | Command-line interface. |
| CORS | Cross-Origin Resource Sharing | Browser mechanism that defines which sites may call an API on another origin. |
| CSS | Cascading Style Sheets | Styling language for web pages. |
| DDL | Data Definition Language | SQL statements that define the database structure (CREATE, ALTER). |
| DEX | Dalvik Executable | The bytecode format that Android runs. |
| DOM | Document Object Model | The tree representation of a web page in the browser. |
| DPC | Device Policy Controller | Management app that applies policies to an Android device, e.g. kiosk mode. |
| DSL | Domain-Specific Language | Special-purpose language, here embedded in Kotlin (e.g. Gradle Kotlin DSL, Exposed). |
| DTO | Data Transfer Object | Object that carries data between client and server. |
| GCM | Galois/Counter Mode | Mode of operation of AES that provides confidentiality and integrity. |
| HTML | HyperText Markup Language | The markup language of web pages. |
| HTTP / HTTPS | HyperText Transfer Protocol (Secure) | The web's communication protocol; HTTPS is its encrypted form over TLS. |
| I/O | Input/Output | Input/output, e.g. network and database calls. |
| IDE | Integrated Development Environment | Integrated development environment, e.g. Android Studio. |
| IoT | Internet of Things | Internet of Things; connected devices. |
| IP | Internet Protocol | The internet's addressing protocol; an IP address identifies a device on the network. |
| IV | Initialization Vector | Random value used in each encryption so that the same data never gives the same output. |
| JDBC | Java Database Connectivity | Java's standard API for connecting to relational databases. |
| JDK / JRE | Java Development Kit / Java Runtime Environment | The Java development tools and runtime environment. |
| JPA | Jakarta Persistence API | Java standard for mapping objects to tables (e.g. Hibernate). |
| JSON | JavaScript Object Notation | Text format for exchanging structured data. |
| JVM | Java Virtual Machine | The virtual machine that runs Java and Kotlin bytecode. |
| JWT | JSON Web Token | Signed token that carries the user's identity and role. |
| KMP | Kotlin Multiplatform | Kotlin technology for shared code across server, web and Android. |
| LAN | Local Area Network | Local network, e.g. the property's Wi-Fi. |
| LF | Line Feed | The line-break character of Linux and macOS (Windows uses CRLF). |
| ORM | Object-Relational Mapping | Mapping of code objects to database tables. |
| OS | Operating System | Operating system. |
| PIN | Personal Identification Number | Personal identification number, e.g. of the card or the kiosk exit. |
| PMS | Property Management System | Property management system (bookings, availability). |
| PNG | Portable Network Graphics | Image file format. |
| QR | Quick Response (code) | Two-dimensional code read by a camera; here for device activation. |
| R2DBC | Reactive Relational Database Connectivity | Non-blocking (reactive) API for relational databases. |
| RBAC | Role-Based Access Control | Role-based access control. |
| REST | Representational State Transfer | Architectural style for APIs over HTTP, with resources and standard methods. |
| SaaS | Software as a Service | Software offered over the internet as a subscription service. |
| SDK | Software Development Kit | Set of tools and libraries for developing on a platform. |
| SHA-256 | Secure Hash Algorithm, 256 bits | Hash algorithm; here for storing refresh tokens. |
| SLF4J | Simple Logging Facade for Java | Common logging interface for Java libraries. |
| SMTP | Simple Mail Transfer Protocol | Protocol for sending email. |
| SQL | Structured Query Language | Query language of relational databases. |
| TLS / SSL | Transport Layer Security / Secure Sockets Layer | Protocols that encrypt network connections; STARTTLS upgrades a connection to an encrypted one. |
| UI | User Interface | User interface. |
| URL | Uniform Resource Locator | Address of a resource on the internet. |
| UTC | Coordinated Universal Time | Coordinated Universal Time, the basis of timestamps. |
| UUID | Universally Unique Identifier | 128-bit identifier that is unique in practice. |
| Wasm | WebAssembly | Binary code format that runs in the browser; WasmGC adds garbage collection. |
| ΑΦΜ | Αριθμός Φορολογικού Μητρώου (VAT ID) | A business's tax number; here for the company registry lookup. |
| ΤΠΕ | Τεχνολογίες Πληροφορίας και Επικοινωνιών (ICT) | Information and communication technologies. |
