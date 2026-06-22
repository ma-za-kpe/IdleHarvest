# IdleHarvest

<p align="center">
  <strong>Privacy-first, on-device agentic system for monetizing idle phone resources in emerging markets</strong>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#installation">Installation</a> •
  <a href="#building">Building</a> •
  <a href="#testing">Testing</a> •
  <a href="#project-structure">Structure</a> •
  <a href="#implementation-log">Log</a>
</p>

---

## Overview

IdleHarvest runs on Arm-powered phones using lightweight AI agents that autonomously detect, optimize, and monetize unused airtime, data bundles, bandwidth, storage, and compute resources. Users earn USDC/tokens via programmable payment rails while all reasoning and raw data stays on-device.

Built with **Kotlin Multiplatform (KMP)** for cross-platform reach: Android-first + iOS + Web (Kotlin/WASM). Models are trained on Vast.ai and exported via **ExecuTorch** for edge deployment with **Arm-optimized acceleration** (KleidiAI/SME2/XNNPACK).

**Target:** [Arm AI Optimization Challenge](https://www.arm.com/) — Mobile AI Track.

## Features

- **Idle Resource Detection** — Automatic scanning of airtime, data bundles, bandwidth, storage, and compute
- **Airtime Monetization** — Sell or transfer expiring prepaid bundles via VTU platforms before they expire
- **DePIN Resource Sharing** — Earn passive crypto by sharing idle bandwidth/storage/compute to decentralized networks
- **BLE Mesh Coordination** — Discover nearby peers and pool resources for collective earning power
- **Autonomous Payments** — USDC payouts via Circle Agent Stack with user-defined guardrails
- **On-Device AI** — ExecuTorch inference with Arm-optimized backends, never sending data to the cloud
- **Privacy Vault** — AES-256 encrypted on-device storage for all data and reasoning traces
- **Hardware Security** — Wallet keys in Android Keystore / iOS Secure Enclave, biometric gating
- **Policy Engine** — User-controlled guardrails with per-agent autonomy levels and transaction limits
- **Compliance Engine** — Per-country/carrier rulesets with OTA updates, KYC threshold checks, audit logging

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    User Interface Layer                   │
│            Compose Multiplatform + Kotlin/WASM            │
├─────────────────────────────────────────────────────────┤
│                 Agent Orchestration Layer                 │
│  PolicyManager │ EventBus │ ComplianceEngine              │
│  AirtimeAgent │ DePinAgent │ MeshCoordinator │ EarningEng │
├─────────────────────────────────────────────────────────┤
│                  Intelligence Layer                       │
│          InferenceEngine │ ModelRegistry                  │
├─────────────────────────────────────────────────────────┤
│                Platform Services Layer                    │
│     ResourceMonitor │ PrivacyVault │ SecureKeystore       │
├─────────────────────────────────────────────────────────┤
│              External Integrations                        │
│  VTU Platforms │ DePIN Networks │ Circle │ BLE Peers      │
└─────────────────────────────────────────────────────────┘
```

**Key design decisions:**
1. Agent-per-concern architecture with independent lifecycles
2. Event-driven coordination via central SharedFlow event bus
3. Policy-first execution — all actions route through PolicyManager before execution
4. Privacy-by-architecture — no raw data leaves the device
5. ExecuTorch for on-device inference with Arm-optimized backends
6. Hardware-backed security — wallet keys never leave secure hardware

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.4.0 |
| Multiplatform | Kotlin Multiplatform (KMP) |
| UI | Compose Multiplatform 1.11.1 |
| Serialization | kotlinx.serialization 1.8.1 |
| Async | kotlinx.coroutines 1.10.2 |
| AI Inference | ExecuTorch (KleidiAI, XNNPACK, SME2) |
| Payments | Circle Agent Stack (USDC) |
| Security | Android Keystore / iOS Secure Enclave |
| BLE | Android BluetoothLeScanner / iOS CoreBluetooth |
| Testing | Kotest 5.9.1 (property-based testing) |
| Static Analysis | Detekt 1.23.7 |
| Formatting | Spotless 7.0.2 (ktlint) |
| Build | Gradle 9.1.0 (version catalog) |
| Min SDK | Android API 24 (Android 7.0) |

## Installation

### Prerequisites

- **JDK 11+** (JDK 17 recommended)
- **Android Studio** Ladybug or newer (with KMP plugin)
- **Xcode 15+** (for iOS builds, macOS only)
- **Gradle 9.1+** (bundled via wrapper)

### Clone

```bash
git clone https://github.com/ma-za-kpe/IdleHarvest.git
cd IdleHarvest
```

### Setup

1. Open in Android Studio
2. Sync Gradle (automatic)
3. Install pre-commit hooks:
   ```bash
   chmod +x scripts/install-hooks.sh
   ./scripts/install-hooks.sh
   ```

## Building

### Android

```bash
# Debug build
./gradlew androidApp:assembleDebug

# Release build
./gradlew androidApp:assembleRelease
```

### Shared Module (all targets)

```bash
# Compile commonMain metadata (fastest verification)
./gradlew shared:compileCommonMainKotlinMetadata

# Compile Android target
./gradlew shared:compileAndroidMain

# Compile iOS targets
./gradlew shared:compileKotlinIosArm64
./gradlew shared:compileKotlinIosSimulatorArm64
```

### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode and build from there. The shared framework is automatically included.

## Testing

### Run All Tests

```bash
# Run all shared module tests (commonTest via Android host)
./gradlew shared:testAndroidHostTest

# Run with verbose output
./gradlew shared:testAndroidHostTest --info
```

### Test Categories

The project uses **Kotest property-based testing** with 34 correctness properties:

| Property | Component | Validates |
|----------|-----------|-----------|
| Resource Profile Completeness | ResourceMonitor | Req 1.1, 1.3 |
| Thermal/Power State Adaptation | ResourceMonitor, InferenceEngine | Req 1.5, 7.6, 10.3 |
| Monetization Recommendation | AirtimeAgent | Req 2.1 |
| Policy & Compliance Gating | PolicyManager, ComplianceEngine | Req 2.6, 5.2, 6.4 |
| Retry Logic Invariant | AirtimeAgent | Req 2.3, 17.5 |
| Privacy Vault Round-Trip | PrivacyVault | Req 2.4, 3.4, 6.5 |
| DePIN Threshold Enforcement | DePinAgent | Req 3.2, 3.3 |
| Cryptographic Proof Validity | DePinAgent | Req 3.6 |
| Peer Roster Invariant | MeshCoordinator | Req 4.2, 4.4 |
| GATT Connection Capacity | MeshCoordinator | Req 4.3 |
| Mesh Communication Encryption | MeshCoordinator | Req 4.5 |
| Adaptive Scan Interval | MeshCoordinator | Req 4.6 |
| Idempotent Payout Guarantee | EarningEngine | Req 5.5 |
| Biometric Gating by Value | EarningEngine, SecureKeystore | Req 5.7, 13.4 |
| Policy Transaction Limits | PolicyManager | Req 6.3 |
| Policy Immediate Application | PolicyManager | Req 6.2 |
| Inference Fallback Guarantee | InferenceEngine | Req 7.5 |
| Vault Encryption Round-Trip | PrivacyVault | Req 8.1 |
| Anonymization Before Transmission | PrivacyVault | Req 8.3 |
| Secure Deletion Completeness | PrivacyVault | Req 8.5 |
| Model Integrity Verification | ModelRegistry | Req 9.3 |
| Model Rollback Availability | ModelRegistry | Req 9.5 |
| Benchmark Statistics Correctness | InferenceEngine | Req 11.3 |
| Benchmark Report Serialization | InferenceEngine | Req 11.5 |
| Compliance Audit Completeness | ComplianceEngine | Req 12.4 |
| Secure Keystore Signing | SecureKeystore | Req 13.3 |
| Biometric Lockout | SecureKeystore | Req 13.5 |
| Circuit Breaker State Machine | CircuitBreaker | Req 16.1 |
| Rolling Error Log Window | RollingErrorLog | Req 16.2 |
| Offline Queue & Retry | OfflineQueue | Req 16.3 |
| Watchdog Agent Restart | AgentWatchdog | Req 16.5 |
| System Health Indicator | SystemHealthIndicator | Req 16.6 |

### Code Quality

```bash
# Run Detekt static analysis
./gradlew detekt

# Run Spotless formatting check
./gradlew spotlessCheck

# Apply Spotless formatting fixes
./gradlew spotlessApply

# Full validation pipeline (tests + lint + detekt + spotless)
./gradlew shared:testAndroidHostTest detekt spotlessCheck
```

## Project Structure

```
IdleHarvest/
├── androidApp/                    # Android application module
│   ├── src/main/
│   │   ├── kotlin/.../service/    # Foreground service, WorkManager
│   │   ├── AndroidManifest.xml    # Permissions, service declarations
│   │   └── res/                   # Android resources
│   └── build.gradle.kts
├── shared/                        # Kotlin Multiplatform shared module
│   ├── src/
│   │   ├── commonMain/kotlin/com/maku/idleharvest/
│   │   │   ├── domain/
│   │   │   │   ├── interfaces/    # Core agent interfaces
│   │   │   │   ├── models/        # Data models, enums, value classes
│   │   │   │   └── SecureKeystore.kt  # expect declaration
│   │   │   ├── infrastructure/    # Implementations
│   │   │   │   ├── crypto/        # CryptoProvider, SimpleCryptoProvider
│   │   │   │   ├── Default*.kt    # Agent implementations
│   │   │   │   ├── CircuitBreaker.kt
│   │   │   │   ├── OfflineQueue.kt
│   │   │   │   ├── AgentWatchdog.kt
│   │   │   │   └── ...
│   │   │   └── ui/               # Compose UI components & theme
│   │   ├── commonTest/kotlin/com/maku/idleharvest/
│   │   │   ├── generators/        # Kotest Arb generators
│   │   │   ├── fakes/             # Test fakes
│   │   │   └── infrastructure/    # Property-based tests (34 tests)
│   │   ├── androidMain/           # Android platform implementations
│   │   │   └── kotlin/.../
│   │   │       ├── domain/        # SecureKeystore.android.kt
│   │   │       └── infrastructure/# BleAdapter, PlatformResourceScanner, Clock
│   │   └── iosMain/               # iOS platform stubs
│   └── build.gradle.kts
├── iosApp/                        # iOS app (Xcode project)
├── web/                           # Web assets & branding
├── config/detekt/                 # Detekt configuration
├── scripts/                       # Pre-commit hooks
├── gradle/
│   └── libs.versions.toml         # Version catalog
├── build.gradle.kts               # Root build (Spotless config)
└── settings.gradle.kts
```

## Configuration

### Version Catalog (`gradle/libs.versions.toml`)

All dependencies are managed centrally. Key versions:
- Kotlin: 2.4.0
- Compose Multiplatform: 1.11.1
- Kotest: 5.9.1
- kotlinx.coroutines: 1.10.2
- kotlinx.serialization: 1.8.1
- AGP: 9.0.1
- Detekt: 1.23.7

### Android Permissions

The app requires these permissions (declared in `AndroidManifest.xml`):
- `FOREGROUND_SERVICE` — persistent background monitoring
- `POST_NOTIFICATIONS` — foreground service notification
- `ACCESS_NETWORK_STATE` — bandwidth scanning
- `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` — BLE mesh
- `WAKE_LOCK` — Doze mode adaptation
- `RECEIVE_BOOT_COMPLETED` — service restart after reboot

## Contributing

1. Create a feature branch from `main`
2. Write property-based tests for new correctness guarantees
3. Ensure all tests pass: `./gradlew shared:testAndroidHostTest`
4. Run quality checks: `./gradlew detekt spotlessCheck`
5. The pre-commit hook will block commits that fail quality gates
6. Open a PR for review

## Implementation Log

Below is the development log tracking implementation progress against the spec.

### Wave 0 — Foundation (Tasks 1.1–1.3) ✅
- Defined all core domain models with `@Serializable` annotations (11 enums, 3 value classes, 15+ data classes)
- Created 11 core interfaces for all agents and services
- Set up Kotest property testing with 14 custom `Arb` generators
- Added kotlinx.serialization, kotlinx.coroutines dependencies
- Created `expect class SecureKeystore` with actual stubs for Android + iOS

### Wave 1 — Event Bus & Privacy Vault (Tasks 1.4, 2.1) ✅
- Implemented `DefaultAgentEventBus` using `MutableSharedFlow` (replay=0, buffer=64)
- Implemented `DefaultPrivacyVault` with AES-256 encryption via `CryptoProvider` abstraction
- Created `SimpleCryptoProvider` (XOR-based) for commonMain testing
- Created platform `expect`/`actual` for `currentTimeMillis()`
- 5 unit tests for event bus passing

### Wave 2 — Privacy & Policy Tests (Tasks 2.2–2.5, 3.1, 3.4) ✅
- Property tests: vault round-trip, encryption, anonymization, secure deletion
- Implemented `DefaultPolicyManager` with autonomy levels, transaction limits, vault persistence
- Implemented `DefaultComplianceEngine` with rate limits, KYC checks, daily/monthly caps, audit logging
- All 34 property tests for vault, policy, compliance passing

### Wave 3 — Policy/Compliance Tests + Resource Monitor (Tasks 3.2–3.6, 5.1) ✅
- Property tests for policy limits, immediate application, gating, audit completeness
- Implemented `DefaultResourceMonitor` with thermal adaptation, battery awareness, graceful failures
- Created `PlatformResourceScanner` expect/actual with platform stubs

### Wave 4 — Android Monitor + Inference + Model Registry (Tasks 5.2, 6.1, 6.2) ✅
- Full Android `PlatformResourceScanner` with BatteryManager, ConnectivityManager, StatFs, PowerManager
- `ResourceMonitorService` foreground service + `ResourceMonitorWorker` (WorkManager backup)
- `DefaultInferenceEngine` with thermal-based backend selection, heuristic fallback, benchmarking
- `DefaultModelRegistry` with SHA-256 integrity, resumable downloads, rollback retention

### Wave 5 — Resource/Inference Tests + Benchmarking (Tasks 5.3–5.4, 6.3–6.6) ✅
- Property tests for resource profile completeness, thermal adaptation
- Property tests for inference fallback, model integrity, model rollback
- `BenchmarkJsonExporter` for Arm Performix-compatible JSON output
- `ModelSizeComparison` for quantization gain reporting

### Wave 6 — Benchmark Tests + Airtime Agent + DePIN Agent (Tasks 6.7–6.8, 8.1, 9.1) ✅
- Property tests for benchmark statistics correctness and serialization round-trip
- `DefaultAirtimeAgent` with 72-hour expiry detection, VTU retry (3x exponential backoff), policy/compliance gating
- `DefaultDePinAgent` with network registration, threshold enforcement, proof generation, connectivity handling

### Wave 7 — Agent Tests + Mesh Coordinator (Tasks 8.2–8.3, 9.2–9.3, 10.1) ✅
- Property tests for monetization recommendations, retry invariant, DePIN thresholds, cryptographic proofs
- `DefaultMeshCoordinator` with BLE abstraction, max 5 GATT connections, adaptive scan, AES-GCM encryption
- `BleAdapter` expect/actual with stubs for Android and iOS

### Wave 8 — BLE Android + Mesh Tests (Tasks 10.2–10.6) ✅
- Full Android `BleAdapter` with BluetoothLeScanner, GATT connections, advertising
- BLE permissions in AndroidManifest (BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE)
- Property tests for peer roster, GATT capacity, mesh encryption, adaptive scan interval

### Wave 9 — Earning Engine + Secure Keystore (Tasks 12.1–12.2) ✅
- `DefaultEarningEngine` with idempotent payouts, biometric gating, nanopayments, Circle Agent Stack abstraction
- Full Android `SecureKeystore` with EC key generation, SHA256withECDSA signing, biometric lockout, mnemonic generation

### Wave 10 — Resilience Infrastructure + Tests (Tasks 12.3–12.6, 13.1–13.8) ✅
- Property tests for idempotent payout, biometric gating, keystore signing, biometric lockout
- `CircuitBreaker` with Closed/Open/HalfOpen state machine (5 failures, 60s backoff)
- `OfflineQueue` with FIFO retry, vault persistence, retry count tracking
- `AgentWatchdog` with 60-second timeout, restart logging
- `RollingErrorLog` (7-day window) + `SystemHealthIndicator` (GREEN/YELLOW/RED)
- Property tests for all resilience components

### Remaining Work
- Onboarding flow (5 screens) and multi-language support
- Kotlin/WASM web dashboard
- Firebase Hosting + App Distribution CI/CD pipeline
- Validation Pipeline and pre-commit hooks configuration
- Integration wiring (all agents → Event Bus → Policy Manager)
- Android MainActivity with DI and agent lifecycle orchestration
- End-to-end integration tests

## License

See [LICENSE](LICENSE) for details.

---

<p align="center">
  Built for the <strong>Arm AI Optimization Challenge</strong> — Mobile AI Track
</p>
