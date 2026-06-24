# IdleHarvest

<p align="center">
  <strong>Privacy-first, on-device agentic system for monetizing idle phone resources in emerging markets</strong>
</p>

<p align="center">
  <a href="https://idleharvest-86163.web.app"><img src="https://img.shields.io/badge/Live%20Demo-Firebase-FF6F00?logo=firebase&logoColor=white" alt="Live Demo" /></a>
  &nbsp;
  <a href="https://github.com/ma-za-kpe/IdleHarvest"><img src="https://img.shields.io/badge/Source-GitHub-181717?logo=github&logoColor=white" alt="GitHub" /></a>
</p>

<p align="center">
  <a href="#try-idleharvest">Try It</a> •
  <a href="#functionality--output">Functionality</a> •
  <a href="#features">Features</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#installation">Installation</a> •
  <a href="#building">Building</a> •
  <a href="#testing">Testing</a> •
  <a href="#project-structure">Structure</a>
</p>

> **Web dashboard live at:** https://idleharvest-86163.web.app

---

## Try IdleHarvest

| Platform | How to access |
|----------|--------------|
| **Web** | Visit [idleharvest-86163.web.app](https://idleharvest-86163.web.app) — no install needed |
| **Android (Beta APK)** | [Get beta via Firebase App Distribution](https://appdistribution.firebase.dev/i/e65c460a68b20fc4) |

> Beta builds require Android 7.0+ (API 24) on any Arm64 device.

---

## Overview

IdleHarvest runs on Arm-powered phones using lightweight AI agents that autonomously detect, optimize, and monetize unused airtime, data bundles, bandwidth, storage, and compute resources. Users earn USDC/tokens via programmable payment rails while all reasoning and raw data stays on-device.

Built with **Kotlin Multiplatform (KMP)** for cross-platform reach: Android-first + iOS + Web (Kotlin/WASM). Models are trained on Vast.ai and exported via **ExecuTorch** for edge deployment with **Arm-optimized acceleration** (KleidiAI/SME2/XNNPACK).

On the Android dashboard, the agent cards are tappable. Selecting a card opens a live inspector pane with the agent's runtime state, current action mode, device posture, mesh status, and the latest settlement signal so judges can see the system moving, not just reading static numbers.

**Target:** [Arm AI Optimization Challenge](https://www.arm.com/) — Mobile AI Track.

## Functionality & Output

### What IdleHarvest does

IdleHarvest runs three always-on AI agents in the background of your Android phone:

| Agent | What it does | Output |
|-------|-------------|--------|
| **Airtime Agent** | Monitors prepaid airtime and data bundles. When a bundle is within 72 hours of expiry and below the usage threshold, the agent initiates a VTU sell or peer transfer. | USDC deposited to your Circle wallet |
| **DePIN Agent** | Shares idle bandwidth, storage, and compute to decentralized physical infrastructure networks (Helium, Filecoin, etc.) within user-defined limits. | Passive crypto earnings per resource-hour |
| **Mesh Coordinator** | Scans for nearby IdleHarvest peers over BLE, pools idle resources to unlock higher-tier earning opportunities, and routes nanopayments between devices. | Collective earnings split by contribution |

### Final output

- **On-device**: A continuously updated `EarningsSummary` (total USDC, last 24h, last 7d, breakdown by source)
- **Wallet**: USDC settled to a Circle-managed wallet with biometric-gated withdrawals
- **Audit log**: Encrypted on-device ledger of every agent action, policy decision, and payout
- **Web dashboard**: Live earnings view at [idleharvest-86163.web.app](https://idleharvest-86163.web.app)
- **Optimized model**: ExecuTorch `.pte` bundle (`<10 MB`, `<100ms` inference) for on-device resource classification

### Privacy guarantees

All agent reasoning, raw telemetry, and personal data remain on-device. The Privacy Vault encrypts everything with AES-256. Only USDC settlement messages leave the device, and only after explicit policy approval.

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

## How To Use The Features

Use the app as a guided demo rather than a hidden background service. The main screens are designed so judges can see the full loop quickly.

### 1. Start from onboarding

- Open the app and complete the onboarding flow.
- Set your guardrails so the Policy Engine can permit safe autonomous actions.
- Enable the permissions the app asks for so background scanning can run.

### 2. Watch agent activity on the dashboard

- Open the main dashboard after onboarding.
- Tap an agent card to open its live inspector.
- Use that inspector to show the agent state, device posture, mesh status, and the latest settlement signal.

### 3. Exercise resource detection

- The Idle Resource Detection path scans airtime, data bundles, bandwidth, storage, and compute.
- When resources are available, the corresponding agent card should move into an active or evaluating state.
- Judges can see the current posture in the dashboard headline and the live inspector.

### 4. Demonstrate airtime monetization

- Show the Airtime Agent on the dashboard.
- Explain that it watches prepaid airtime and data bundles for expiry or low-usage conditions.
- Use the phone balance probe to call `TelephonyManager.sendUssdRequest(...)` through the Android app and read a live balance from the device instead of a hardcoded demo value.
- If the probe returns a valid balance, the manual sale button becomes available so the user can trigger a sale from the same screen.
- When the policy engine allows it, the agent can trigger a sale or transfer before the value expires.

### 5. Demonstrate DePIN sharing

- Show the DePIN Agent card.
- Explain that it contributes idle bandwidth, storage, or compute only within the user limits.
- The output is presented as passive earnings rather than opaque background work.

### 6. Demonstrate mesh coordination

- Show the Mesh Coordinator card and the connected peers section.
- Use that area to explain how nearby devices pool resources over BLE for larger earning opportunities.
- The live peer roster makes the distributed system visible to the user.

### 7. Demonstrate payments and trust

- Show the earnings summary and recent transactions.
- Explain that Autonomous Payments settle into the wallet flow via Circle Agent Stack.
- Point out the Privacy Vault, Hardware Security, Policy Engine, and Compliance Engine as the guardrails that keep the system safe.

### 8. Use the buyer loop

- Open the buyer portal from the landing page.
- The landing page card opens the hosted `/buyer` route directly, so judges can see the buyer side without navigating away from the web demo.
- Run the Ktor buyer backend locally if you want the full demo loop.
- Download the trained `.pte` artifact through the backend route to show that the model asset is real and served end to end.
- The hosted web app also exposes the buyer portal at `/buyer` through Firebase Hosting, so judges can open the buyer side without a local backend.
- The dashboard cards now include info icons and helper copy so it is obvious why each agent is active, when it can earn, and when the app is only notifying instead of acting.

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

## Vast.ai Training

Use Vast.ai to fine-tune and export your own on-device model artifacts.

### Full lifecycle

Use this flow for a short training run that stays up for at most 1 hour:

1. Create or select an SSH key in your Vast.ai account.
2. Provision a GPU instance.
3. SSH into the instance and run `bash ml/vastai_setup.sh`.
4. Watch `ml/output/train.log` until training finishes.
5. Export `ml/output/idleharvest_model.pte`.
6. Verify the artifact, download it, then destroy the instance as soon as you are satisfied.

### What actually happened in practice

The first pass used a remote fine-tune path, but Hugging Face access and export tooling were not stable enough inside the Vast.ai environment. To keep the challenge moving, the training script now falls back to an offline `local-scratch` GPT-2 style run when the hosted base model cannot be used.

That fallback was the reliable path for the final artifact:

- training completed on Vast.ai with a real loss curve and a non-trivial final checkpoint
- the merged checkpoint was exported locally when the ExecuTorch toolchain was easier to control on Windows
- the final `.pte` artifact was then served through the buyer backend so the app could download it from a real route instead of a stub registry

### Access the instance

```bash
# List your active instances
vastai show instances-v1

# Inspect a specific instance and grab the SSH host/port
vastai show instance <INSTANCE_ID> --raw

# Connect over SSH using the host and port from the instance output
ssh -o BatchMode=yes -p <SSH_PORT> root@<SSH_HOST>
```

If `vastai` is not on your PATH, use the Python module form:

```bash
python -m vastai show instances
python -m vastai show instance <INSTANCE_ID> --raw
```

If the module is missing, install it first:

```bash
python -m pip install vastai
```

### Provision a fresh training instance

```bash
# Show search options and pick an offer that matches your GPU budget
vastai search offers 'gpu_name=RTX_4090 num_gpus=1 verified=true direct_port_count>=1 rentable=true' -o 'dlperf_usd-'

# Create an SSH-enabled instance with your public key attached
vastai create ssh-key ~/.ssh/id_ed25519.pub
vastai create instance <OFFER_ID> --image pytorch/pytorch:2.4.0-cuda12.4-cudnn9-runtime --disk 20 --ssh --direct

# Get the SSH URL or raw host/port details
vastai ssh-url <INSTANCE_ID>
vastai show instance <INSTANCE_ID> --raw
```

### Train and export

```bash
git clone https://github.com/ma-za-kpe/IdleHarvest.git
cd IdleHarvest
bash ml/vastai_setup.sh

# Or run the steps manually:
python3 ml/generate_dataset.py --rows 10000 --out ml/data
python3 ml/train.py --data ml/data/device_usage.jsonl --base_model local-scratch --output_dir ml/output/lora_merged --epochs 3 --batch_size 8
python3 ml/export_to_executorch.py --model_dir ml/output/lora_merged --out ml/output/idleharvest_model.pte --quantize int8
```

If Hugging Face access is available, `ml/train.py` keeps the original QLoRA path. If not, it falls back to an offline scratch GPT-2 style model so the repo can still produce a real trained checkpoint and `.pte` artifact on Vast.ai.

The exporter strips stale 4-bit quantization metadata from `config.json` before loading the merged checkpoint, so an already-trained model can still be exported cleanly.

For this repo, the final `.pte` export now uses the documented ExecuTorch flow:
`torch.export` -> `to_edge_transform_and_lower` -> `to_executorch()`.

On Windows, the exporter auto-detects the `flatc.exe` bundled in the ExecuTorch wheel. If you run the export manually in a custom environment, you can point `FLATC_EXECUTABLE` at that binary before launching the script.

### Verify the training output

```bash
# Training is complete when the log contains "Training complete."
grep -n "Training complete" ml/output/train.log
tail -n 50 ml/output/train.log

# Confirm the merged checkpoint exists and is non-empty
du -sh ml/output/lora_merged

# Confirm the exported ExecuTorch artifact exists
ls -lh ml/output/idleharvest_model.pte
```

### Tear down

Destroy the instance as soon as the export has been verified. Do not leave it running longer than 1 hour.

```bash
vastai destroy instance <INSTANCE_ID> -y
```

The exported model file is written on the Vast.ai instance at:

```bash
~/IdleHarvest/ml/output/idleharvest_model.pte
```

The quickest way to prove the training finished is to verify all of these:

```bash
grep -n "Training complete" ml/output/train.log
ls -lh ml/output/idleharvest_model.pte
du -sh ml/output/lora_merged
```

After export, copy it down to your machine and into the app resources if you want to ship it with the app:

```bash
scp -P <SSH_PORT> root@<SSH_HOST>:~/IdleHarvest/ml/output/idleharvest_model.pte .
cp idleharvest_model.pte shared/src/commonMain/composeResources/files/
```

## Buyer Backend

IdleHarvest now includes a tiny Ktor backend that simulates the buyer side of the ecosystem. The Firebase-hosted landing page routes into this buyer flow so the demo shows both the earning side and the demand side in one loop.

### Run locally

```bash
./gradlew buyerBackend:run
```

Keep that terminal open while you are testing. The service binds to `http://127.0.0.1:8080`, so it only exists while the machine running it is powered on and the process is alive.

If you shut down the computer, you must start it again before hitting:

```text
http://127.0.0.1:8080/health
http://127.0.0.1:8080/api/buyer/summary
```

Recommended startup order for a full local demo:

1. Start the buyer backend with `./gradlew buyerBackend:run`
2. In a second terminal, start the Android app or connect the device
3. If the phone app needs the backend over USB, run `adb reverse tcp:8080 tcp:8080`
4. Open the landing page or the app and verify the buyer route

The backend listens on `http://127.0.0.1:8080` and exposes:

- `GET /health`
- `GET /api/buyer/summary`
- `POST /api/buyer/orders`
- `GET /api/models`
- `GET /api/models/idleharvest_model`
- `GET /api/models/idleharvest_model.pte`

### Android device demo

If you are running the app on a USB-connected Android device, forward the backend port first:

```bash
adb reverse tcp:8080 tcp:8080
```

Then the app can download the model artifact from `http://127.0.0.1:8080/api/models/idleharvest_model.pte`.

### Buyer route

- Open the landing page and click `Open Buyer Portal`.
- Or navigate directly to `/buyer`.

### Why this matters

The buyer portal closes the demo loop:

- the landing page introduces the buyer side without leaving the app
- the Ktor backend serves model metadata, the trained `.pte`, and buyer settlement responses
- the dashboard can surface buyer activity as a real signal instead of a fake placeholder
- judges can follow the full story from training to deployment to simulated demand

## Artifact & Version Management

IdleHarvest keeps source code and build artifacts separate on purpose:

- The trained ExecuTorch `.pte` file is generated locally and served through the backend or release storage.
- The `.pte` artifact is intentionally not committed to git.
- The app and backend code that regenerate or download the artifact are versioned in source control.
- The Android container preloads `idleharvest_model` on startup so the downloaded `.pte` becomes part of the runtime inference path instead of sitting unused on disk.
- APK tester builds are versioned through Gradle using `versionCode` and `versionName`, with the beta package name and file name derived from those values.
- When you publish a tester build, use the Gradle distribution task so testers always receive a clearly versioned APK from Firebase App Distribution.
- The distribution task targets the `internal-testers` group by default and can also take an explicit tester list via `-PappDistributionTesters=you@example.com` or `APP_DISTRIBUTION_TESTERS=you@example.com` if you need to force delivery to a specific account.
- Firebase App Distribution sends testers an onboarding email when a build is shared with them; if a tester does not receive the APK, confirm the exact email is in the tester group, check spam, and make sure the tester accepted the invite for the same Google account.
- The Android dashboard now surfaces a sell-eligibility nudge when the Airtime Agent sees an expiring bundle or airtime balance, so judges do not have to guess why the app wants to sell.
- Trigger-driven notifications now fire for bundle expiry, earnings, policy blocks, and nearby mesh peers so the user sees earning opportunities as events instead of hidden background state.
- The phone balance probe now uses the device as the source of truth; if the phone does not return a value, the UI shows unavailable instead of inventing one.

Current app version metadata lives in [`androidApp/build.gradle.kts`](/C:/Users/nampa/AndroidStudioProjects/IdleHarvest/androidApp/build.gradle.kts). The beta output is named from the app version so releases are traceable during judging and bug triage.

## TODO / Roadmap

The next demo loop should make the full ecosystem visible from training to buyer settlement.

- [x] Re-run Vast.ai export until `ml/output/idleharvest_model.pte` is produced and archived.
- [x] Confirm the exported model can be downloaded from a real artifact endpoint instead of a no-op registry.
- [x] Add a minimal buyer backend using Ktor that simulates demand, orders, and settlement callbacks.
- [x] Share common models and business rules between the Android app and backend with Kotlin Multiplatform.
- [x] Extend the web landing page with a buyer-side panel or route so the demo shows both sides of the loop.
- [ ] Add fake payout and buyer transaction data for demo mode so the ecosystem can be shown without real third-party keys.
- [x] Keep the beta APK task Windows-safe and runnable from this repo on any developer machine.
- [x] Add backend integration tests for the buyer artifact/download flow.
- [ ] Add backend integration tests for buyer/order/settlement flows.
- [x] Wire the Android phone balance probe to USSD instead of demo airtime values.
- [x] Document the training, export, backend, and artifact download flow in the repo.

## Building

### Android

```bash
# Debug build
./gradlew androidApp:assembleDebug

# Release build
./gradlew androidApp:assembleRelease

# Beta APK for tester distribution (output: dist/IdleHarvest-beta-1.0.apk)
./gradlew androidApp:buildBetaApk
```

### Arm64 Device — Build, Sideload & Validate

> Tested on: Pixel 6 (Tensor G2 / Arm Cortex-X1), Samsung Galaxy A54 (Exynos 1380 / Arm Cortex-A78)

**Step 1 — Enable Developer Options on your device**
```
Settings → About phone → tap "Build number" 7 times
Settings → Developer options → enable "USB debugging"
```

**Step 2 — Build and install**
```bash
# Build debug APK
./gradlew androidApp:assembleDebug

# Install directly over ADB (device must be connected via USB)
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

**Step 3 — Validate agents are running**
```bash
# Tail logcat for agent events
adb logcat -s IdleHarvest:V AgentOrchestrator:V ResourceMonitor:V

# Check WorkManager background task is scheduled
adb shell dumpsys jobscheduler | grep idleharvest
```

**Step 4 — Run tests on an Arm64 host (CI / Arm Virtual Hardware)**
```bash
# All 180+ property-based tests (runs on JVM via Android host test)
./gradlew shared:testAndroidHostTest

# Verify Arm-specific optimizations compile
./gradlew shared:compileKotlinIosArm64     # cross-compile for Arm64
./gradlew shared:wasmJsBrowserDistribution # Kotlin/WASM production bundle
```

**Step 5 — Verify performance targets**

| Metric | Target | How to measure |
|--------|--------|---------------|
| Inference latency | < 100ms | `adb logcat -s InferenceEngine` |
| Model size | < 10 MB | `ls -lh shared/src/commonMain/ml/` |
| Battery impact | < 5%/hr | Android Battery & power settings → app usage |
| Background memory | < 64 MB | `adb shell dumpsys meminfo com.maku.idleharvest` |

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
