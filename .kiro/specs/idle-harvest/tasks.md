# Implementation Plan: IdleHarvest

## Overview

This plan implements IdleHarvest — a privacy-first, on-device agentic system for monetizing idle phone resources — using Kotlin Multiplatform (KMP) with shared business logic in `commonMain` and platform-specific implementations in `androidMain`, `iosMain`, and `wasmJsMain`. The implementation follows an agent-per-concern architecture with event-driven coordination, policy-first execution, and hardware-backed security.

## Tasks

- [x] 1. Set up project structure, core interfaces, and testing infrastructure
  - [x] 1.1 Define core domain models and value classes in shared/commonMain
    - Create `ResourceProfile`, `AirtimeBalance`, `DataBundle`, `AirtimeTransaction`, `DePinContribution`, `EarningEvent`, `EarningsSummary`, `AgentState`, value classes (`AgentId`, `PeerId`, `WalletAddress`), and all enums (`ThermalState`, `TransactionType`, `TransactionOutcome`, `ResourceType`, `EarningSource`, `AutonomyLevel`, `PayoutStatus`, `MeshState`, `ModelPurpose`, `QuantizationLevel`)
    - Add `kotlinx.serialization` annotations for all models
    - _Requirements: 1.1, 2.4, 3.4, 5.1, 9.1_

  - [x] 1.2 Define core interfaces in shared/commonMain
    - Create interfaces: `ResourceMonitor`, `AirtimeAgent`, `DePinAgent`, `MeshCoordinator`, `EarningEngine`, `PolicyManager`, `InferenceEngine`, `PrivacyVault`, `ComplianceEngine`, `ModelRegistry`, `AgentEventBus`
    - Define `MonitorConfig`, `ResourceThreshold`, `Policy`, `PolicyDecision`, `ComplianceRuleSet`, `ComplianceDecision`, `MonetizationRecommendation`, `BenchmarkReport`, `DeviceMetadata`, `ModelMetadata`, sealed classes for `AgentEvent`
    - Create expect declaration for `SecureKeystore`
    - _Requirements: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1, 9.1, 12.1, 13.1_

  - [x] 1.3 Set up Kotest property testing framework and custom generators
    - Add Kotest dependencies (`kotest-property`, `kotest-assertions-core`, `kotest-runner-junit5`) to shared module
    - Create `shared/src/commonTest/kotlin/com/maku/idleharvest/generators/` directory with custom Arb generators for `ResourceProfile`, `Policy`, `AirtimeBundle`, `ComplianceRuleSet`, `BenchmarkReport`, `Peer`, and other domain types
    - Create fake implementations directory `shared/src/commonTest/kotlin/com/maku/idleharvest/fakes/`
    - _Requirements: 18.1, 18.7_

  - [x] 1.4 Implement Event Bus for agent coordination
    - Implement `AgentEventBus` using Kotlin coroutines `SharedFlow`
    - Support typed event publishing and subscription with `KClass`-based filtering
    - _Requirements: 1.1, 2.1, 3.1, 4.1_

- [x] 2. Implement Privacy Vault and encryption layer
  - [x] 2.1 Implement Privacy Vault abstraction and AES-256 encryption in commonMain
    - Implement the `PrivacyVault` interface with AES-256-GCM encryption
    - Create key-value storage with the key patterns defined in the data models (resource_profile_latest, tx_airtime_{id}, policy_{id}, etc.)
    - Implement secure deletion (`deleteAll`) that removes all stored data
    - Implement integrity checking (`isIntegrityValid`)
    - Implement `exportAnonymized` with consent token validation and PII stripping
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 2.2 Write property test for Privacy Vault storage round-trip
    - **Property 6: Privacy Vault Storage Round-Trip**
    - **Validates: Requirements 2.4, 3.4, 6.5, 9.1, 12.1**

  - [x] 2.3 Write property test for vault encryption
    - **Property 18: Vault Encryption Round-Trip**
    - **Validates: Requirements 8.1**

  - [x] 2.4 Write property test for anonymization before transmission
    - **Property 19: Anonymization Before Transmission**
    - **Validates: Requirements 8.3**

  - [x] 2.5 Write property test for secure deletion completeness
    - **Property 20: Secure Deletion Completeness**
    - **Validates: Requirements 8.5**

- [x] 3. Implement Policy Manager and Compliance Engine
  - [x] 3.1 Implement Policy Manager with guardrail enforcement
    - Implement `PolicyManager` interface with in-memory policy store backed by Privacy_Vault persistence
    - Implement per-agent autonomy levels (manual, semi-automatic, fully automatic)
    - Implement configurable transaction limits per agent per time period
    - Implement policy validation, immediate application to running agents, and violation logging
    - Provide sensible defaults for first-time users (conservative limits, manual approval for financial actions)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [x] 3.2 Write property test for policy transaction limit enforcement
    - **Property 15: Policy Transaction Limit Enforcement**
    - **Validates: Requirements 6.3**

  - [x] 3.3 Write property test for policy immediate application
    - **Property 16: Policy Immediate Application**
    - **Validates: Requirements 6.2**

  - [x] 3.4 Implement Compliance Engine with configurable rulesets
    - Implement `ComplianceEngine` interface with per-country/carrier rulesets
    - Implement transaction rate limits, daily/monthly caps, and KYC threshold checks
    - Implement audit logging for all compliance checks (approved and denied) with timestamps
    - Support over-the-air rule updates without app update
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

  - [x] 3.5 Write property test for policy and compliance gating
    - **Property 4: Policy and Compliance Gating**
    - **Validates: Requirements 2.6, 5.2, 6.4, 12.2, 12.3**

  - [x] 3.6 Write property test for compliance audit completeness
    - **Property 25: Compliance Audit Completeness**
    - **Validates: Requirements 12.4**

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement Resource Monitor and background execution
  - [x] 5.1 Implement Resource Monitor in commonMain with platform expect/actual pattern
    - Implement `ResourceMonitor` interface with `StateFlow<ResourceProfile>` emission
    - Implement configurable scan interval (default 15 minutes), graceful failure handling (log failures, continue remaining scans)
    - Implement thermal state adaptation and battery-level-aware frequency reduction
    - Implement cold-start scan completing within 5 seconds target
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 5.2 Implement Android-specific Resource Monitor (androidMain)
    - Create `AndroidResourceMonitor` using `BatteryManager`, `ConnectivityManager`, `StorageStatsManager`
    - Implement foreground service notification for persistent background execution
    - Implement Doze mode adaptation via `AlarmManager.setAndAllowWhileIdle`
    - Implement WorkManager restart when OS terminates background service
    - Implement battery threshold monitoring (default 20%) with activity reduction
    - Implement charger-connected resume for full operation
    - _Requirements: 1.5, 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7_

  - [x] 5.3 Write property test for resource profile completeness
    - **Property 1: Resource Profile Completeness**
    - **Validates: Requirements 1.1, 1.3**

  - [x] 5.4 Write property test for thermal and power state adaptation
    - **Property 2: Thermal and Power State Adaptation**
    - **Validates: Requirements 1.5, 7.6, 10.3, 10.4, 10.6**

- [x] 6. Implement Inference Engine and Model Registry
  - [x] 6.1 Implement Inference Engine with ExecuTorch integration
    - Implement `InferenceEngine` interface with model loading, inference execution, and hot-swap capability
    - Implement backend selection based on thermal state (KleidiAI, XNNPACK, SME2, CPU_BASELINE)
    - Implement dynamic model switching (smaller model when hot, full model when cool/charging)
    - Implement CPU utilization cap (max 30% of available cores)
    - Implement fallback to rule-based heuristics on model failure
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_

  - [x] 6.2 Implement Model Registry with lifecycle management
    - Implement `ModelRegistry` interface with model metadata storage (version, size, hardware profile, quantization level)
    - Implement metered-aware download (prefer Wi-Fi), resumable downloads from checkpoint
    - Implement SHA-256 integrity verification before making models available
    - Implement rollback retention (previous version kept until new is confirmed stable)
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [x] 6.3 Write property test for inference fallback guarantee
    - **Property 17: Inference Fallback Guarantee**
    - **Validates: Requirements 7.5**

  - [x] 6.4 Write property test for model integrity verification
    - **Property 21: Model Integrity Verification**
    - **Validates: Requirements 9.3**

  - [x] 6.5 Write property test for model rollback availability
    - **Property 22: Model Rollback Availability**
    - **Validates: Requirements 9.5**

  - [x] 6.6 Implement benchmarking subsystem
    - Implement `runBenchmark` with standardized 100-pass test suite reporting min, max, mean, p95 latency
    - Include memory usage, power draw, and device metadata in reports
    - Output JSON format compatible with Arm Performix tooling
    - Report model size reduction (FP32 vs quantized)
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_

  - [x] 6.7 Write property test for benchmark statistics correctness
    - **Property 23: Benchmark Statistics Correctness**
    - **Validates: Requirements 11.3**

  - [x] 6.8 Write property test for benchmark report serialization round-trip
    - **Property 24: Benchmark Report Serialization Round-Trip**
    - **Validates: Requirements 11.5**

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement Airtime Agent and VTU integration
  - [x] 8.1 Implement Airtime Agent with monetization logic
    - Implement `AirtimeAgent` interface with state machine (idle → evaluating → executing → settling)
    - Implement expiry detection (72-hour window) and recommendation generation (sell, transfer, hold)
    - Implement VTU platform transaction execution with retry logic (3 retries, exponential backoff)
    - Implement real-time transaction status display
    - Record all transactions in Privacy_Vault (amount, counterparty, timestamp, platform, outcome)
    - Integrate with Policy_Manager and Compliance_Engine for pre-execution validation
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 8.2 Write property test for monetization recommendation generation
    - **Property 3: Monetization Recommendation Generation**
    - **Validates: Requirements 2.1**

  - [x] 8.3 Write property test for retry logic invariant
    - **Property 5: Retry Logic Invariant**
    - **Validates: Requirements 2.3, 17.5**

- [x] 9. Implement DePIN Agent
  - [x] 9.1 Implement DePIN Agent with resource sharing logic
    - Implement `DePinAgent` interface with network registration, contribution management, and threshold enforcement
    - Implement dynamic contribution adjustment (reduce/pause within 10 seconds when resources drop below threshold)
    - Implement token earning tracking per network per session in Privacy_Vault
    - Implement graceful disconnect on connectivity loss with pending proof queuing
    - Implement cryptographic proof generation for contribution verification
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 9.2 Write property test for DePIN threshold enforcement
    - **Property 7: DePIN Threshold Enforcement**
    - **Validates: Requirements 3.2, 3.3**

  - [x] 9.3 Write property test for cryptographic proof validity
    - **Property 8: Cryptographic Proof Validity**
    - **Validates: Requirements 3.6**

- [x] 10. Implement Mesh Coordinator with BLE peer discovery
  - [x] 10.1 Implement Mesh Coordinator in commonMain with BLE abstraction
    - Implement `MeshCoordinator` interface with peer discovery, connection management, and roster maintenance
    - Enforce max 5 simultaneous GATT connections with advertising rotation for additional peers
    - Implement peer disconnect handling (remove from roster, redistribute tasks within 5 seconds)
    - Implement AES-GCM authenticated encryption for all inter-device communication
    - Implement adaptive scan intervals (aggressive when charging, conservative on battery)
    - Use direct GATT connections and advertising-based discovery only (no BLE Mesh flooding/relaying)
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

  - [x] 10.2 Implement Android BLE platform bindings (androidMain)
    - Create actual BLE implementation using Android's `BluetoothLeScanner`, `BluetoothGattServer`, `BluetoothGattCallback`
    - Handle BLE permissions (BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE)
    - _Requirements: 4.1_

  - [x] 10.3 Write property test for peer roster invariant
    - **Property 9: Peer Roster Invariant**
    - **Validates: Requirements 4.2, 4.4**

  - [x] 10.4 Write property test for GATT connection capacity invariant
    - **Property 10: GATT Connection Capacity Invariant**
    - **Validates: Requirements 4.3**

  - [x] 10.5 Write property test for mesh communication encryption
    - **Property 11: Mesh Communication Encryption**
    - **Validates: Requirements 4.5**

  - [x] 10.6 Write property test for adaptive scan interval
    - **Property 12: Adaptive Scan Interval**
    - **Validates: Requirements 4.6**

- [x] 11. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Implement Earning Engine and Secure Keystore
  - [x] 12.1 Implement Earning Engine with Circle Agent Stack integration
    - Implement `EarningEngine` interface with payout initiation via Circle Agent Stack
    - Implement idempotent payout guarantee (reject duplicate payouts for same earning event)
    - Implement agent-to-agent nanopayments for inter-device services
    - Enforce all policies from Policy_Manager before executing financial transactions
    - Log rejection reasons and notify user on payout failures
    - Integrate biometric requirement for high-value transactions
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [x] 12.2 Implement Secure Keystore (Android actual implementation)
    - Implement `SecureKeystore` actual class using Android Keystore API
    - Generate and store private signing keys within hardware security module
    - Implement signing operations within secure hardware (return only signature, never export key)
    - Implement biometric authentication gating for high-value transactions
    - Implement lockout after 3 consecutive biometric failures (configurable cooldown)
    - Implement mnemonic phrase backup/recovery (generated and displayed once during setup)
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6_

  - [x] 12.3 Write property test for idempotent payout guarantee
    - **Property 13: Idempotent Payout Guarantee**
    - **Validates: Requirements 5.5**

  - [x] 12.4 Write property test for biometric gating by transaction value
    - **Property 14: Biometric Gating by Transaction Value**
    - **Validates: Requirements 5.7, 13.4**

  - [x] 12.5 Write property test for secure keystore signing correctness
    - **Property 26: Secure Keystore Signing Correctness**
    - **Validates: Requirements 13.3**

  - [x] 12.6 Write property test for biometric lockout after consecutive failures
    - **Property 27: Biometric Lockout After Consecutive Failures**
    - **Validates: Requirements 13.5**

- [x] 13. Implement error handling, resilience, and monitoring
  - [x] 13.1 Implement Circuit Breaker pattern
    - Implement `CircuitBreaker` class with state machine (Closed → Open → HalfOpen)
    - Open after 5 consecutive failures, configurable backoff period
    - Wrap all external service calls (VTU, DePIN, Circle) with circuit breakers
    - _Requirements: 16.1_

  - [x] 13.2 Implement Offline Queue and watchdog timers
    - Implement `OfflineQueue` for pending transactions, proofs, and submissions during offline periods
    - Automatic retry when connectivity returns
    - Implement `AgentWatchdog` with 60-second timeout and agent restart capability
    - _Requirements: 16.3, 16.5_

  - [x] 13.3 Implement rolling error log and system health indicator
    - Implement rolling 7-day on-device error log accessible via diagnostics screen
    - Implement system health indicator derivation (green/yellow/red) based on agent states and connectivity
    - Implement anonymized crash reports (with user consent) for critical failures — no PII
    - _Requirements: 16.2, 16.4, 16.6_

  - [x] 13.4 Write property test for circuit breaker state machine
    - **Property 30: Circuit Breaker State Machine**
    - **Validates: Requirements 16.1**

  - [x] 13.5 Write property test for rolling error log window
    - **Property 31: Rolling Error Log Window**
    - **Validates: Requirements 16.2**

  - [x] 13.6 Write property test for offline queue and retry
    - **Property 32: Offline Queue and Retry**
    - **Validates: Requirements 16.3**

  - [x] 13.7 Write property test for watchdog agent restart
    - **Property 33: Watchdog Agent Restart**
    - **Validates: Requirements 16.5**

  - [x] 13.8 Write property test for system health indicator derivation
    - **Property 34: System Health Indicator Derivation**
    - **Validates: Requirements 16.6**

- [x] 14. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 15. Implement onboarding, localization, and user experience
  - [x] 15.1 Implement onboarding flow (max 5 screens)
    - Create step-by-step onboarding: welcome, permission grants, wallet setup, guardrail configuration, first resource scan
    - Include educational tooltips explaining each agent capability and earning mechanism
    - Apply safe defaults when user skips steps; allow revisiting from settings
    - _Requirements: 15.1, 15.2, 15.5_

  - [x] 15.2 Implement multi-language support and accessibility
    - Set up localization framework supporting English, French, Swahili, Hausa
    - Implement locale-aware currency display (local currency + USDC equivalent), date/time formatting
    - Implement accessibility: screen reader compatibility, 48dp touch targets, scalable text, high-contrast mode
    - Implement offline-first experience with cached data availability
    - _Requirements: 15.3, 15.4, 15.6_

  - [x] 15.3 Write property test for localization completeness
    - **Property 28: Localization Completeness**
    - **Validates: Requirements 14.6, 15.3**

  - [x] 15.4 Write property test for safe defaults on incomplete onboarding
    - **Property 29: Safe Defaults on Incomplete Onboarding**
    - **Validates: Requirements 15.5**

- [x] 16. Implement Web Dashboard (Kotlin/WASM)
  - [x] 16.1 Create Kotlin/WASM web dashboard in wasmJsMain
    - Build marketing landing page with product overview, features, download links, and impact statistics
    - Implement authenticated lite earnings dashboard (total earnings, recent transactions, active agents, device status)
    - Share UI components and business logic with mobile app via shared KMP module
    - Ensure responsive design and WCAG 2.1 AA compliance
    - Implement privacy-preserving sync API client (no sensitive data stored, fetched on-demand with E2E encryption)
    - Add multi-language content support (English, French, Swahili, Hausa)
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6_

- [x] 17. Implement CI/CD pipeline with Firebase deployment
  - [x] 17.1 Set up Firebase Hosting deployment pipeline
    - Configure Firebase CLI for Kotlin/WASM web asset deployment
    - Implement deployment validation (HTTP 200 check within 60 seconds)
    - Implement failure handling (retain previous live version, notify team)
    - Implement concurrent deployment prevention and preview channel support for PRs
    - _Requirements: 17.1, 17.3, 17.4, 17.7, 17.8_

  - [x] 17.2 Set up Firebase App Distribution pipeline
    - Configure upload for Android and iOS test builds with tester group notification
    - Tag releases with build version, commit SHA, and build timestamp
    - Implement retry logic (3 retries, exponential backoff) on upload failure
    - _Requirements: 17.2, 17.5, 17.6_

  - [x] 17.3 Set up Validation Pipeline and pre-commit hooks
    - Configure Detekt for Kotlin static analysis with project ruleset
    - Configure Spotless for code formatting enforcement
    - Set up pre-commit hook executing: unit tests, lint, Detekt, Spotless
    - Block commits on any quality gate failure
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 18.7, 18.8_

- [x] 18. Integration wiring and final assembly
  - [x] 18.1 Wire all agents to Event Bus and Policy Manager
    - Connect Resource_Monitor events to Airtime_Agent (bundle expiry detection)
    - Connect Resource_Monitor events to DePIN_Agent (threshold monitoring)
    - Connect Mesh_Coordinator peer events to Earning_Engine (nanopayments)
    - Ensure all agent actions route through Policy_Manager and Compliance_Engine before execution
    - Wire thermal and connectivity events to all adaptive subsystems
    - _Requirements: 1.1, 2.6, 3.2, 5.2, 6.4, 10.6_

  - [x] 18.2 Implement Android MainActivity with DI and agent lifecycle orchestration
    - Set up dependency injection for all agents and services
    - Initialize foreground service, register WorkManager jobs
    - Wire UI (Compose Multiplatform) to agent state flows and earning summaries
    - Implement compliance disclaimer/consent flow during onboarding
    - _Requirements: 10.1, 12.5, 15.1_

  - [x] 18.3 Write integration tests for end-to-end agent flows
    - Test full agent lifecycle: resource detection → recommendation → policy check → execution → settlement
    - Test offline queue behavior: generate earning while offline → reconnect → auto-retry
    - Test graceful degradation order under resource constraints
    - _Requirements: 2.1, 2.2, 5.1, 16.1, 16.3_

- [x] 19. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (34 properties total)
- Unit tests validate specific examples and edge cases
- The implementation uses Kotlin Multiplatform with `commonMain` for shared logic and platform-specific `actual` implementations
- All property tests use Kotest property testing library as specified in the design
- iOS (`iosMain`) implementations are deferred beyond initial Android-first delivery but interfaces are ready

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["1.4", "2.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "2.4", "2.5", "3.1", "3.4"] },
    { "id": 3, "tasks": ["3.2", "3.3", "3.5", "3.6", "5.1"] },
    { "id": 4, "tasks": ["5.2", "6.1", "6.2"] },
    { "id": 5, "tasks": ["5.3", "5.4", "6.3", "6.4", "6.5", "6.6"] },
    { "id": 6, "tasks": ["6.7", "6.8", "8.1", "9.1"] },
    { "id": 7, "tasks": ["8.2", "8.3", "9.2", "9.3", "10.1"] },
    { "id": 8, "tasks": ["10.2", "10.3", "10.4", "10.5", "10.6"] },
    { "id": 9, "tasks": ["12.1", "12.2"] },
    { "id": 10, "tasks": ["12.3", "12.4", "12.5", "12.6", "13.1", "13.2", "13.3"] },
    { "id": 11, "tasks": ["13.4", "13.5", "13.6", "13.7", "13.8"] },
    { "id": 12, "tasks": ["15.1", "15.2", "16.1"] },
    { "id": 13, "tasks": ["15.3", "15.4", "17.1", "17.2", "17.3"] },
    { "id": 14, "tasks": ["18.1", "18.2"] },
    { "id": 15, "tasks": ["18.3"] }
  ]
}
```
