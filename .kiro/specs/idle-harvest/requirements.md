# Requirements Document

## Introduction

IdleHarvest is a privacy-first, on-device agentic system for monetizing idle phone resources in emerging markets. Running on Arm-powered phones, it uses lightweight AI agents to autonomously detect, optimize, and monetize unused airtime, data bundles, bandwidth, storage, and compute resources. Users earn USDC/tokens via programmable payment rails while all reasoning and raw data stays on-device. Models are trained/fine-tuned on Vast.ai and exported via ExecuTorch for edge deployment. The system targets the Arm AI Optimization Challenge (Mobile AI Track) and uses Kotlin Multiplatform for cross-platform reach (Android-first + iOS + Web landing page via Kotlin/WASM).

## Glossary

- **Resource_Monitor**: The subsystem responsible for detecting and tracking idle phone resources (airtime balance, data bundles, bandwidth, storage, compute availability)
- **Airtime_Agent**: The AI agent that predicts airtime/data usage patterns and expiry, then automates sales or transfers via VTU platforms
- **DePIN_Agent**: The AI agent that manages opt-in sharing of idle bandwidth, storage, and compute to decentralized physical infrastructure networks
- **Mesh_Coordinator**: The subsystem that forms and manages BLE peer discovery and opportunistic pairing with nearby phones for pooled resource sharing and peer-to-peer exchange
- **Earning_Engine**: The subsystem that manages autonomous earning decisions, USDC payouts via Circle Agent Stack, and agent-to-agent nanopayments
- **Inference_Engine**: The on-device AI inference subsystem using ExecuTorch with Arm-optimized acceleration (KleidiAI/SME2/XNNPACK)
- **Policy_Manager**: The subsystem that stores and enforces user-defined guardrails controlling agent autonomy boundaries
- **User_Guardrails**: The set of user-defined rules, limits, and policies that constrain agent autonomy (transaction caps, resource sharing thresholds, auto-approval levels)
- **VTU_Platform**: Virtual Top-Up platform APIs (e.g., Prestmit, VTU.ng) used for airtime/data sales and transfers
- **DePIN_Network**: Decentralized Physical Infrastructure Networks (e.g., Grass, Titan Network) that compensate resource contributors
- **Privacy_Vault**: The on-device encrypted storage for all raw data, models, and reasoning traces
- **Model_Registry**: The subsystem managing deployed .pte model files, their versions, and update lifecycle
- **Resource_Profile**: A snapshot of current device idle resources including available airtime, data, bandwidth, storage, and compute capacity
- **Compliance_Engine**: The subsystem that validates agent actions against local regulations, carrier terms of service, and anti-fraud rules
- **Web_Dashboard**: The Kotlin/WASM web landing page providing marketing, onboarding, and a lite earnings dashboard accessible from any browser
- **Secure_Keystore**: Platform-specific secure storage (Android Keystore / iOS Keychain / Secure Enclave) for wallet keys and signing credentials
- **Firebase_Hosting**: Google's web hosting service used to deploy and serve the Kotlin/WASM web landing page and dashboard with CDN-backed delivery
- **Firebase_App_Distribution**: Google's pre-release testing service used to distribute Android and iOS test builds to designated testers without app store submission
- **Validation_Pipeline**: The automated sequence of quality checks (tests, lint, static analysis, formatting) that runs after every core milestone and before code is committed
- **Detekt**: A static code analysis tool for Kotlin that identifies code smells, complexity issues, and potential bugs
- **Spotless**: A code formatting enforcement tool that applies and verifies consistent code style rules across the project
- **Pre_Commit_Hook**: A Git hook script that executes the Validation_Pipeline checks automatically before a commit is finalized, blocking commits that fail quality gates

## Requirements

### Requirement 1: Idle Resource Detection

**User Story:** As a phone owner in an emerging market, I want the system to automatically detect my idle resources, so that I know what can be monetized without manual tracking.

#### Acceptance Criteria

1. WHEN the device boots or the app is launched, THE Resource_Monitor SHALL scan and report a Resource_Profile containing airtime balance, data bundle status, available bandwidth, free storage, and idle compute capacity within 5 seconds
2. WHILE the app is running in the background, THE Resource_Monitor SHALL update the Resource_Profile at a configurable interval (default 15 minutes)
3. IF the Resource_Monitor fails to access a resource metric, THEN THE Resource_Monitor SHALL log the failure reason and continue monitoring remaining resources
4. THE Resource_Monitor SHALL consume less than 2% additional battery per hour during background monitoring on a mid-range device (measured via Arm Performix)
5. WHEN the device enters Doze mode or thermal throttling, THE Resource_Monitor SHALL adapt its scan frequency to avoid system conflicts

### Requirement 2: Airtime and Data Monetization

**User Story:** As a user with expiring prepaid bundles, I want an agent to automatically sell or transfer my unused airtime/data before it expires, so that I recover value that would otherwise be lost.

#### Acceptance Criteria

1. WHEN unused airtime or data bundles are detected with an expiry within 72 hours, THE Airtime_Agent SHALL generate a monetization recommendation (sell, transfer, or hold)
2. WHEN the user approves a monetization action or the Policy_Manager has pre-approved automatic actions, THE Airtime_Agent SHALL execute the transaction via the configured VTU_Platform API
3. IF a VTU_Platform API call fails, THEN THE Airtime_Agent SHALL retry up to 3 times with exponential backoff and notify the user on final failure
4. THE Airtime_Agent SHALL record all transactions including amount, counterparty, timestamp, platform used, and outcome in the Privacy_Vault
5. WHILE a transaction is in progress, THE Airtime_Agent SHALL display the transaction status to the user in real time
6. THE Airtime_Agent SHALL validate all transactions against Compliance_Engine rules before execution (carrier ToS, daily rate limits, KYC thresholds)

### Requirement 3: DePIN Resource Sharing

**User Story:** As a user with idle bandwidth and storage, I want to opt in to sharing those resources with decentralized networks, so that I earn passive crypto income.

#### Acceptance Criteria

1. WHEN the user opts in to DePIN sharing for a specific resource type, THE DePIN_Agent SHALL register the device with the selected DePIN_Network
2. WHILE the device has idle resources exceeding user-defined thresholds, THE DePIN_Agent SHALL contribute those resources to the registered DePIN_Network
3. IF device resource usage rises above the user-defined threshold, THEN THE DePIN_Agent SHALL reduce or pause DePIN contributions within 10 seconds to avoid impacting user experience
4. THE DePIN_Agent SHALL track and report earned tokens per DePIN_Network per session in the Privacy_Vault
5. WHEN the DePIN_Agent detects network connectivity loss, THE DePIN_Agent SHALL gracefully disconnect from the DePIN_Network and queue pending proofs for later submission
6. THE DePIN_Agent SHALL generate cryptographic proofs of contribution (bandwidth served, compute completed) for verifiable earning claims

### Requirement 4: BLE Peer Discovery and Coordination

**User Story:** As a user near other IdleHarvest devices, I want my phone to discover nearby peers and coordinate pooled resource sharing, so that collective earning power increases.

#### Acceptance Criteria

1. WHEN Bluetooth is enabled and the user has opted in to mesh participation, THE Mesh_Coordinator SHALL scan for nearby IdleHarvest peers using BLE advertising and GATT discovery
2. WHILE connected to peers, THE Mesh_Coordinator SHALL maintain a roster of active peers with their available Resource_Profiles
3. THE Mesh_Coordinator SHALL support up to 5 simultaneous GATT peer connections (with additional peers discoverable via advertising rotation)
4. IF a peer disconnects unexpectedly, THEN THE Mesh_Coordinator SHALL remove the peer from the active roster and redistribute any pooled tasks within 5 seconds
5. WHILE connected to peers, THE Mesh_Coordinator SHALL encrypt all inter-device communication using authenticated encryption (AES-GCM)
6. THE Mesh_Coordinator SHALL implement adaptive scan intervals (aggressive when charging, conservative on battery) to balance discovery with power consumption
7. THE Mesh_Coordinator SHALL NOT attempt full BLE Mesh flooding/relaying — only direct GATT connections and advertising-based discovery are used

### Requirement 5: Autonomous Earning and Payments

**User Story:** As a user, I want agents to autonomously earn and receive payments within my defined guardrails, so that I generate passive income without constant oversight.

#### Acceptance Criteria

1. WHEN an agent completes a monetizable action, THE Earning_Engine SHALL initiate a payout request via the Circle Agent Stack for USDC settlement
2. THE Earning_Engine SHALL enforce all active policies from the Policy_Manager before executing any financial transaction
3. IF a payout request is rejected by the payment rail, THEN THE Earning_Engine SHALL log the rejection reason and notify the user
4. THE Earning_Engine SHALL support agent-to-agent nanopayments for inter-device services within a peer network
5. WHILE the Earning_Engine is processing a payout, THE Earning_Engine SHALL not initiate additional payouts for the same earning event (idempotent payout guarantee)
6. THE Earning_Engine SHALL store wallet signing keys exclusively in the Secure_Keystore (Android Keystore / iOS Keychain) and never in application memory or shared storage
7. FOR transactions exceeding user-defined high-value thresholds, THE Earning_Engine SHALL require biometric confirmation before signing

### Requirement 6: Policy and Guardrails Management

**User Story:** As a user, I want to define guardrails that control what agents can do autonomously, so that I maintain control over my device and finances.

#### Acceptance Criteria

1. THE Policy_Manager SHALL provide a configuration interface for setting per-agent autonomy levels (manual approval, semi-automatic, fully automatic)
2. WHEN a policy is created or updated, THE Policy_Manager SHALL validate the policy for completeness and apply it immediately to running agents
3. THE Policy_Manager SHALL enforce maximum transaction limits per agent per time period (configurable by the user)
4. IF an agent attempts an action that violates an active policy, THEN THE Policy_Manager SHALL block the action and log the violation with the action details
5. THE Policy_Manager SHALL persist all policies in the Privacy_Vault so they survive app restarts
6. THE Policy_Manager SHALL provide sensible defaults for first-time users (conservative limits, manual approval for financial actions)

### Requirement 7: On-Device AI Inference

**User Story:** As a user, I want AI models running entirely on my device with minimal battery impact, so that I get intelligent resource decisions without cloud dependency or privacy loss.

#### Acceptance Criteria

1. THE Inference_Engine SHALL load and execute ExecuTorch .pte model files using Arm-optimized backends (KleidiAI, XNNPACK, or SME2 where available)
2. THE Inference_Engine SHALL complete a single inference pass for usage prediction within 100ms on a mid-range Arm device (Cortex-A76 class)
3. WHILE executing inference, THE Inference_Engine SHALL limit CPU utilization to a maximum of 30% of available cores
4. WHEN a new model version is available in the Model_Registry, THE Inference_Engine SHALL hot-swap models without restarting background services
5. IF a model fails to load or produces an error during inference, THEN THE Inference_Engine SHALL fall back to rule-based heuristics and log the model failure
6. THE Inference_Engine SHALL support dynamic model switching based on device thermal state (smaller model when hot, full model when cool/charging)

### Requirement 8: Privacy and On-Device Data Protection

**User Story:** As a privacy-conscious user, I want all my data and AI reasoning to remain on-device, so that my personal information is never exposed without my explicit consent.

#### Acceptance Criteria

1. THE Privacy_Vault SHALL encrypt all stored data at rest using AES-256
2. THE Privacy_Vault SHALL store all raw resource data, transaction history, model outputs, and reasoning traces exclusively on-device
3. WHEN data must leave the device (proofs, anonymized metrics), THE Privacy_Vault SHALL require explicit user consent and apply anonymization before transmission
4. IF the device storage encryption is compromised or tampered with, THEN THE Privacy_Vault SHALL lock access to stored data and notify the user
5. THE Privacy_Vault SHALL support secure deletion of all user data on user request within a single operation

### Requirement 9: Model Lifecycle Management

**User Story:** As a developer/operator, I want to manage model versions deployed to devices, so that agents always run the most accurate and efficient models.

#### Acceptance Criteria

1. THE Model_Registry SHALL store metadata for each deployed model including version, size, target hardware profile, and quantization level
2. WHEN a model update is available, THE Model_Registry SHALL download the new .pte file over a metered-aware connection (preferring Wi-Fi)
3. THE Model_Registry SHALL verify model integrity via SHA-256 checksum before making a model available to the Inference_Engine
4. IF a model update download is interrupted, THEN THE Model_Registry SHALL resume the download from the last checkpoint
5. THE Model_Registry SHALL retain the previous model version as a rollback target until the new version is confirmed stable

### Requirement 10: Background Execution and Battery Optimization

**User Story:** As a user, I want the system to run continuously in the background with minimal battery drain, so that earning never stops even when I am not actively using my phone.

#### Acceptance Criteria

1. THE Resource_Monitor SHALL operate as a persistent background service on Android using a foreground service notification
2. WHILE running in the background, THE entire IdleHarvest system SHALL target <5% total battery per hour under normal operation (validated on real Arm devices — actual numbers may vary by chipset)
3. WHEN battery level drops below a user-configurable threshold (default 20%), THE Resource_Monitor SHALL reduce monitoring frequency and pause non-essential agent activities
4. THE Resource_Monitor SHALL resume full operation when the device is connected to a charger
5. IF the operating system terminates the background service, THEN THE Resource_Monitor SHALL reschedule restart using WorkManager (Android) or BGTaskScheduler (iOS)
6. THE Resource_Monitor SHALL adapt to thermal throttling by reducing agent activity when device temperature exceeds safe operating thresholds
7. WHEN the device enters Doze mode (Android) or background app refresh restrictions (iOS), THE Resource_Monitor SHALL comply with OS scheduling constraints and batch operations accordingly

### Requirement 11: Arm Performance Benchmarking

**User Story:** As a challenge submission, I want to demonstrate measurable Arm optimizations, so that judges can evaluate the quality of hardware-specific acceleration.

#### Acceptance Criteria

1. THE Inference_Engine SHALL log inference latency, memory usage, and power draw per model execution
2. THE Inference_Engine SHALL produce a benchmark report comparing Arm-optimized backends (KleidiAI, XNNPACK, SME2) against a baseline CPU-only execution
3. WHEN a benchmark run is triggered, THE Inference_Engine SHALL execute a standardized test suite of 100 inference passes and report min, max, mean, and p95 latency
4. THE Inference_Engine SHALL report model size reduction achieved through quantization (original FP32 vs deployed INT8/INT4)
5. THE Inference_Engine SHALL output benchmark results in a machine-readable JSON format compatible with Arm Performix tooling
6. THE benchmark report SHALL include device metadata (SoC model, core config, RAM, OS version) for reproducibility
7. THE Inference_Engine SHALL target: model size <10MB, single inference <100ms, background system power target <5% battery/hr (validated on real devices)

### Requirement 12: Compliance and Risk Management

**User Story:** As a user in a regulated market, I want the system to respect local regulations, carrier rules, and anti-fraud policies, so that my accounts are never suspended and my actions remain legal.

#### Acceptance Criteria

1. THE Compliance_Engine SHALL maintain a configurable ruleset per country/carrier covering transaction rate limits, daily/monthly caps, and KYC thresholds
2. BEFORE executing any VTU transaction, THE Airtime_Agent SHALL query the Compliance_Engine for approval based on current rules
3. IF a transaction would exceed a regulatory threshold (e.g., KYC limit), THEN THE Compliance_Engine SHALL block the action and notify the user with the specific regulation
4. THE Compliance_Engine SHALL log all compliance checks (approved and denied) with timestamps for audit purposes
5. THE Compliance_Engine SHALL provide a disclaimer/consent flow during onboarding informing users of risks associated with automated airtime/data resale
6. THE Compliance_Engine SHALL support over-the-air rule updates without requiring an app update

### Requirement 13: Wallet Security and Key Management

**User Story:** As a user earning real money, I want my wallet keys stored with hardware-level security, so that my funds cannot be stolen even if the app is compromised.

#### Acceptance Criteria

1. THE Secure_Keystore SHALL generate and store all private signing keys within the device's hardware security module (Android Keystore / iOS Secure Enclave)
2. THE Secure_Keystore SHALL NEVER export raw private keys to application memory or logs
3. WHEN a transaction requires signing, THE Secure_Keystore SHALL perform the signing operation within the secure hardware and return only the signature
4. FOR high-value transactions (above user-configured threshold), THE Secure_Keystore SHALL require biometric authentication (fingerprint or face) before signing
5. IF biometric authentication fails 3 consecutive times, THEN THE Secure_Keystore SHALL lock signing operations for a configurable cooldown period and notify the user
6. THE Secure_Keystore SHALL support key backup/recovery via user-controlled mnemonic phrase (generated and displayed only once during setup)

### Requirement 14: Web Landing Page and Dashboard

**User Story:** As a prospective or existing user, I want a web-accessible landing page and lite dashboard built with Kotlin, so that I can learn about IdleHarvest, track earnings, and manage my account from any browser.

#### Acceptance Criteria

1. THE Web_Dashboard SHALL be built using Kotlin/WASM (Compose for Web) sharing UI components and business logic with the mobile app via the shared KMP module
2. THE Web_Dashboard SHALL include a marketing landing page with product overview, features, download links, and impact statistics
3. THE Web_Dashboard SHALL provide an authenticated lite earnings dashboard showing total earnings, recent transactions, active agents, and device status
4. THE Web_Dashboard SHALL be responsive and accessible (WCAG 2.1 AA compliant) across desktop and mobile browsers
5. THE Web_Dashboard SHALL NOT store any sensitive user data — all dashboard data is fetched on-demand from a privacy-preserving sync API with end-to-end encryption
6. THE Web_Dashboard SHALL support multi-language content (English, French, Swahili, Hausa as initial targets for African markets)

### Requirement 15: Onboarding and User Experience

**User Story:** As a first-time user (potentially with low technical literacy), I want a simple, guided onboarding flow, so that I can start earning quickly without confusion.

#### Acceptance Criteria

1. THE App SHALL provide a step-by-step onboarding flow (maximum 5 screens) covering: welcome, permission grants, wallet setup, guardrail configuration, and first resource scan
2. THE onboarding flow SHALL include educational tooltips explaining each agent capability and earning mechanism in plain language
3. THE App SHALL support multi-language UI (English, French, Swahili, Hausa at minimum)
4. THE App SHALL support accessibility features: screen reader compatibility, minimum touch target sizes (48dp), scalable text, and high-contrast mode
5. IF the user skips onboarding steps, THEN THE App SHALL apply safe defaults and allow revisiting setup from settings
6. THE App SHALL provide an offline-first experience — core UI and cached data are available without network connectivity

### Requirement 16: Error Handling, Resilience, and Monitoring

**User Story:** As a user, I want the system to handle failures gracefully and recover automatically, so that earning is never permanently interrupted by transient issues.

#### Acceptance Criteria

1. ALL agent subsystems SHALL implement circuit-breaker patterns: after 5 consecutive failures to an external service, THE agent SHALL pause attempts for a configurable backoff period
2. THE App SHALL maintain an on-device error log (rolling 7-day window) accessible to the user via a diagnostics screen
3. WHEN the device is offline, THE Earning_Engine SHALL queue all pending proofs, transactions, and submissions for automatic retry when connectivity returns
4. THE App SHALL send anonymized crash reports (with user consent) for critical failures only — no PII, no transaction details
5. ALL background agents SHALL implement watchdog timers: if an agent loop is unresponsive for >60 seconds, THE system SHALL restart that agent and log the event
6. THE App SHALL display a system health indicator on the main dashboard (green/yellow/red) summarizing agent and connectivity status

### Requirement 17: Firebase Deployment and Distribution

**User Story:** As a developer, I want to deploy the web landing page to Firebase Hosting and distribute mobile test builds via Firebase App Distribution, so that testers and users can access the latest versions through an automated CI/CD pipeline.

#### Acceptance Criteria

1. WHEN a production build of the Kotlin/WASM web landing page is triggered, THE CI/CD pipeline SHALL deploy the compiled web assets to Firebase_Hosting using the Firebase CLI
2. WHEN a new Android or iOS test build is produced, THE CI/CD pipeline SHALL upload the build artifact to Firebase_App_Distribution and notify designated tester groups
3. THE CI/CD pipeline SHALL validate the Firebase_Hosting deployment by checking that the deployed URL returns an HTTP 200 status within 60 seconds of deployment completion
4. IF the Firebase_Hosting deployment fails, THEN THE CI/CD pipeline SHALL log the failure reason, retain the previous live version, and notify the development team
5. IF the Firebase_App_Distribution upload fails, THEN THE CI/CD pipeline SHALL retry the upload up to 3 times with exponential backoff and notify the development team on final failure
6. THE CI/CD pipeline SHALL tag each Firebase_App_Distribution release with the build version, commit SHA, and build timestamp for traceability
7. WHILE a deployment to Firebase_Hosting is in progress, THE CI/CD pipeline SHALL prevent concurrent deployments to the same hosting channel to avoid conflicts
8. THE CI/CD pipeline SHALL support deployment to Firebase_Hosting preview channels for pull request previews before merging to production

### Requirement 18: Validation Pipeline and Code Quality Gates

**User Story:** As a developer, I want an automated validation pipeline that enforces code quality after every core milestone, so that defects and style violations are caught before code is committed.

#### Acceptance Criteria

1. WHEN a core milestone is achieved (feature complete, refactor complete, or bug fix complete), THE Validation_Pipeline SHALL execute the full quality gate sequence: unit tests, lint checks, Detekt static analysis, and Spotless formatting verification
2. THE project SHALL include Pre_Commit_Hook scripts that execute the Validation_Pipeline automatically before any commit is finalized
3. IF any check in the Validation_Pipeline fails, THEN THE Pre_Commit_Hook SHALL block the commit and report the specific failures to the developer
4. THE Validation_Pipeline SHALL run Detekt configured with the project ruleset for Kotlin static analysis covering code smells, complexity thresholds, and potential bugs
5. THE Validation_Pipeline SHALL run Spotless configured with the project formatting rules for Kotlin code style enforcement
6. THE Validation_Pipeline SHALL execute standard Android lint checks and report warnings and errors
7. THE Validation_Pipeline SHALL execute the full unit test suite and require all tests to pass before the quality gate is satisfied
8. IF Detekt or Spotless is not configured in the project build files, THEN THE CI/CD pipeline SHALL fail the build and report the missing configuration

## Non-Functional Requirements

### Performance

- Single inference pass: <100ms on Cortex-A76 class SoCs
- Resource scan completion: <5 seconds from cold start
- BLE peer discovery: <10 seconds to first peer detection
- VTU transaction round-trip: <30 seconds end-to-end
- App cold launch to dashboard: <3 seconds
- Model size: <10MB per deployed .pte file

### Battery & Power

- Background monitoring: <2% battery/hr
- Full system (all agents active): <5% battery/hr on mid-range device
- BLE scanning (conservative mode): <1% battery/hr additional
- Charging mode: no power restrictions, full agent activity

### Security

- Wallet keys: hardware-backed (Keystore/Secure Enclave), never exported
- Data at rest: AES-256 encryption
- Inter-device communication: AES-GCM authenticated encryption
- No PII transmitted without explicit consent + anonymization

### Scalability & Testing

- Unit tests for all agent decision logic (shared KMP module)
- Integration tests against simulated VTU/DePIN environments
- Multi-device BLE testing with 3+ physical Arm devices
- Performance regression tests run per CI build (inference latency, memory)
- Low-end device testing: target Arm Cortex-A55 (common in African market devices)

### Internationalization

- UI: English, French, Swahili, Hausa (expandable)
- Currency display: local currency + USDC equivalent
- Date/time: locale-aware formatting
- Right-to-left support: not required for initial launch but architecture must not preclude it

## Measurable Success Metrics

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| Inference latency (p95) | <100ms | Arm Performix benchmark suite |
| Model size (quantized) | <10MB | File size of deployed .pte |
| Battery drain (background) | target <5%/hr (validated on real devices) | Arm Performix power profiling |
| BLE peer detection | <10s | Automated multi-device test |
| Airtime sale completion | <30s | End-to-end integration test |
| Cold start to dashboard | <3s | Instrumented app launch |
| Quantization gain | >60% size reduction vs FP32 | Model export comparison |
| Inference speedup (Arm opt) | >2x vs CPU baseline | KleidiAI vs no-delegation benchmark |

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Carrier blocks automated airtime transfers | Earning channel lost | Multi-VTU platform fallback; compliance rate limiting; manual transfer mode |
| BLE connection limits vary by chipset | Fewer peers than expected | Adaptive peer rotation; advertising-only mode for extra peers |
| Battery drain exceeds targets on low-end devices | User churn | Aggressive throttling tiers; thermal-aware scheduling; user-configurable mode |
| Circle Agent Stack API changes | Payment rail breaks | Abstracted payment interface; fallback to direct USDC transfer |
| Regulatory changes in target markets | Legal risk | OTA compliance rule updates; conservative defaults; user disclaimers |
| Model too large for low-end RAM | Inference fails | Multiple model tiers (tiny/small/medium); dynamic selection per device capability |
| DePIN network reliability | Intermittent earnings | Multi-network support; proof queuing; retry logic |
| Low adoption in target markets | Network effects don't materialize | Simple onboarding + educational tooltips + referral incentives |

## Future Extensions

- Integration with additional DePIN networks as they emerge
- Model fine-tuning pipeline: Vast.ai training → ExecuTorch export → OTA deployment
- Multi-agent collaboration beyond BLE (Wi-Fi Direct, local mesh networking)
- Advanced tokenomics: staking earned tokens for yield, community pools
- Expanded VTU market coverage (additional African countries and carriers)
- Desktop companion app (Kotlin/JVM) for power users
- Agent marketplace: users publish/subscribe to custom agent strategies
