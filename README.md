# Free OBD — Open-Source Android OBD-II Vehicle Diagnostic App

**[English](README.md)** | [中文](README.zh-CN.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

<div align="center">

🚗🔧📊

*A professional-grade OBD-II vehicle diagnostic tool built with Kotlin + Jetpack Compose*

</div>

---

## 📋 Overview

Free OBD is a full-featured Android OBD-II diagnostic app that connects to ELM327 adapters via Bluetooth (Classic SPP + BLE) to read real-time vehicle data, diagnostic trouble codes (DTCs), freeze frame data, and vehicle information.

**Built-in Demo mode** — experience all features without any hardware.

### ✨ Core Features

| Feature | Description |
| :--- | :--- |
| **🎮 Demo Mode** | Built-in simulated data engine — full experience without an OBD adapter |
| **Bluetooth Device Scanning** | Simultaneously scan Classic Bluetooth (SPP) and BLE OBD adapters |
| **Auto/Manual Protocol Selection** | Supports all 13 ELM327 protocols: CAN, K-line (ISO 9141-2 / KWP2000), J1850 PWM/VPW, J1939, etc. |
| **K-Line Support** | Motorcycle ECU adaptation — automatic 5-baud / fast init |
| **Encrypted Handshake (AT+SETCRYPT)** | Automatically identifies and extracts the challenge from AT+VERSION and computes the key |
| **Voltage Detection** | Read voltage via ATRV, popup warning on low voltage |
| **Adapter Firmware Info** | ATI version string displayed on the Connected card |
| **ECU Address Configuration** | Supports broadcast address (0x7DF) and specific ECU addresses |
| **PID Bitmap Discovery** | Mode 01/02/09 triple bitmaps queried segment by segment, with block visualization + tap-to-jump |
| **Live Dashboard** | Canvas gauges (gradient arcs + pointer shadows), up to 6 gauges, auto-polling |
| **Live Data Explorer** | Browse all Mode 01 PIDs segment by segment with real-time values |
| **Freeze Frame Data** | Mode 02 bitmap discovery + per-PID pull, with multi-frame prev/next navigation |
| **DTC Read / Clear** | Mode 03/07/0A three-tab view, Mode 04 clear with confirmation dialog |
| **DTC Details** | Built-in SAE J2012 DTC database (loaded from bundled dtc_codes.db) |
| **DTC Offline Lookup** | DTC reference page — paginated browsing, search, configurable page size, page number jump |
| **PID Detail Dialog** | Tap any PID card to view full metadata (description, unit, range, formula), shared across all three Explorer pages |
| **Vehicle Information** | Mode 09 bitmap discovery + pull VIN, Calibration ID, CVN, ECU Name per-item |
| **Debug Console** | Real-time TX/RX command log, SAF save path selection + manual TX input |
| **Runtime Permissions** | Adaptive permission requests for Android 12+ and below |

---

## 🛠️ Tech Stack

| Component | Technology | Version |
| :--- | :--- | :--- |
| **Language** | Kotlin | 2.3.0 |
| **UI** | Jetpack Compose + Material 3 | BOM 2024.10.00 |
| **Architecture** | MVVM + Clean Architecture | — |
| **Async** | Kotlin Coroutines + Flow | 1.8.1 |
| **Database** | Room | 2.7.0 |
| **DI** | Koin | 3.5.6 |
| **OBD Protocol** | kotlin-obd-api (eltonvs) | 1.4.1 |
| **KSP** | KSP2 | 2.3.4 |
| **Min SDK** | API 24 (Android 7.0) | — |
| **Target SDK** | API 34 (Android 14) | — |

---

## 📁 Project Structure

```
app/src/main/java/com/freeobd/app/
├── data/
│   ├── local/                   # Room database
│   │   ├── entity/              # DtcEntity, PidMetadataEntity, VehicleProfileEntity
│   │   ├── dao/                 # DtcDao, PidMetadataDao, VehicleProfileDao
│   │   ├── AppDatabase.kt      # Database singleton
│   │   ├── DtcSeeder.kt         # Seeds bundled DTC database (dtc_codes.db)
│   │   └── PidMetadataSeeder.kt    # Seeds PID metadata JSON (pid_definitions.json, 186 entries)
│   ├── remote/                  # OBD communication layer
│   │   ├── ObdTransport.kt     # Transport layer abstraction interface (SPP/BLE)
│   │   ├── SppTransport.kt     # Classic Bluetooth RFCOMM implementation
│   │   ├── BleTransport.kt     # BLE GATT implementation (skeleton)
│   │   ├── ObdCommandQueue.kt  # Raw ELM327 command queue + Mutex serialization
│   │   ├── ELM327Initializer.kt    # ATZ→ATE0→ATL0→AT+VERSION→(SETCRYPT)→ATSP→ATH0/ATH1→ATSH
│   │   ├── YMOBDCrypto.kt          # Encryption key generation algorithm (crypt: challenge → SETCRYPT key)
│   │   ├── PIDBitmapParser.kt      # Bitmap parser parsePidBitmap() (shared across Mode 01/02/09 Explorers)
│   │   ├── DTCParser.kt           # DTC parser (SAE J2012)
│   │   ├── MultiFrameHandler.kt   # ISO 15765-2 multi-frame reassembly (VIN etc.)
│   │   └── DebugLogger.kt         # Debug log recorder (in-memory buffer)
│   ├── mock/                    # Demo mode
│   │   ├── MockBluetoothRepository.kt  # Simulated Bluetooth scan + connection
│   │   ├── MockOBDRepository.kt       # Simulated OBD data engine (with debug logging)
│   │   └── DemoModeState.kt           # Global real/mock repository switch
│   └── repository/              # Repository implementations
│       ├── BluetoothRepositoryImpl.kt
│       └── OBDRepositoryImpl.kt
├── domain/
│   ├── model/                   # OBDData, DTC, VehicleInfo, ProtocolInfo, Discovery, etc.
│   ├── repository/              # BluetoothRepository, OBDRepository interfaces
│   └── usecase/                 # ConnectBluetooth, ReadLiveData, ReadDTC
├── presentation/
│   ├── bluetooth/               # Bluetooth connection page + Demo toggle + Protocol/Advanced config
│   ├── dashboard/               # Dashboard page (up to 6 gauges) + GaugeWidget + PID picker
│   ├── livedata/                # Live Data Explorer — Mode 01 bitmap + PID browsing
│   ├── freezeframe/             # Freeze Frame — Mode 02 bitmap + multi-frame navigation
│   ├── dtc/                     # DTC page + detail dialog (Stored/Pending/Permanent)
│   ├── dtc_lookup/              # DTC offline lookup page — paginated browsing, search, page size/page control
│   ├── vehicle/                 # Vehicle Information — Mode 09 bitmap + InfoType cards
│   ├── debug/                   # Debug Console page (command log + manual TX)
│   ├── components/              # Shared components (SupportBlockGrid, DetailCard, PidDetailDialog, SupportLegend)
│   ├── theme/                   # Dark theme color scheme (automotive dashboard style)
│   └── navigation/              # NavRoutes + AppNavHost
├── di/
│   └── AppModule.kt            # Koin DI module
└── utils/
    ├── ByteUtils.kt             # Byte/hex utilities
    ├── CoroutineUtils.kt        # Coroutine extensions (timeout, retry, throttle)
    └── Extensions.kt            # General Kotlin extensions
```

---

## 📡 Command Sending Methods

`ObdCommandQueue` provides three command entry points, each with a distinct role — do not mix them up:

| Method | Purpose | 7F Detection | Log Behavior | Callers |
| :--- | :--- | :--- | :--- | :--- |
| `sendRaw` | AT commands (`ATZ`, `ATRV`, `ATSP`, etc.)<br>ELM327 initialization sequence | ❌ Not detected | Logged when `DebugLogger.enabled` | `ELM327Initializer`,<br>`getProtocolInfo`,<br>`readVoltage`, `readAdapterInfo` |
| `sendObdCommand` | OBD mode commands (`010C`, `03`, `0902`, etc.)<br>All 01-0A mode requests | ✅ Detects `7F xx yy`<br>Returns failure on match | Same as above | `discoverLiveDataPIDs`, `discoverFreezeFramePIDs`,<br>`discoverVehicleInfoTypes`, `readPID`,<br>`readDTCsFromMode`, `clearDTCs`,<br>`readFreezeFramePID`, `readLiveDataPID`, etc. |
| `sendRawCommand`<br>(Repository layer) | Debug Console manual TX input<br>Auto-routes AT vs OBD | ✅ OBD commands routed through<br>`sendObdCommand` | **Always logged**<br>Not affected by `DebugLogger.enabled` | `DebugConsoleScreen`<br>manual input field |

> **Principle**: OBD requests for modes 01-0A must use `sendObdCommand`, otherwise 7F negative responses will be silently ignored as "no data".

### 7F Negative Response Codes

When the ECU rejects an OBD request, it returns `7F [service] [code]`. Common error codes:

| Code | Meaning |
| :--- | :--- |
| `11` | serviceNotSupported |
| `12` | subFunctionNotSupported |
| `22` | conditionsNotCorrect |
| `31` | requestOutOfRange |
| `78` | responsePending |

---

## 📐 OBD Response Data Format and Parsing


### extraSkip Mechanism

`extractFromDecoded` skips 2 bytes by default (mode response + 1 sub-byte). `extraSkip` declares additional bytes to skip:

| Scenario | extraSkip | Content Skipped | Notes |
| :--- | :--- | :--- | :--- |
| **Mode 01 / Mode 09 CAN** | `0` | mode + PID / InfoType | No extra bytes |
| **Mode 02 non-CAN** | `1` | mode + PID + **frame number echo** | Frame number echoed as data byte, must skip |
| **Mode 09 non-CAN (count types)** | `0` | mode + InfoType | `0901`, `0903`, `0905`, `0909` — no message count |
| **Mode 09 non-CAN (data types)** | `1` | mode + InfoType + **message count** | `0900`, `0902`, `0904`, `0906`, `0908`, `090A`, `090B` |
| **Mode 09 multi-frame multi-record** | `1` | mode + InfoType + **record index** | Each frame of `0904`, `0906`, `090A` |

> `extraSkip = 0` is the default. All call sites must explicitly declare the number of extra bytes to skip — no implicit protocol inference at the extraction layer.

---

### Mode 02: CAN vs Non-CAN Frame Number Echo

Mode 02 responses have the format `42 PID FRAME [data]`, where `FRAME` is the freeze frame number (0x00–0xFF).

| Protocol | Frame Number Location | extraSkip | Reason |
| :--- | :--- | :--- | :--- |
| **CAN** (Protocols 6–A) | Handled by CAN multi-frame protocol layer | **0** | Frame number carried by ISO 15765-2 flow control frames, not duplicated in OBD data area |
| **Non-CAN** (Protocols 1–5) | First byte of OBD data area | **1** | K-line / J1850 have no multi-frame protocol layer; frame number echoed as independent data byte |

Example — query Mode 02 PID 02 frame 00 (command `020200`):

```
CAN     response: 42 02 00 01 70 3B
                  ↑↑ ↑↑      ↑↑↑↑
                  md PID      data + padding
                  (frame 00 handled by CAN layer, not in OBD data)

Non-CAN response: 42 02 00 01 70 C3
                  ↑↑ ↑↑ ↑↑   ↑↑↑↑
                  md PID FRM  data + padding
                  (frame 00 echoed as data byte, extraSkip=1 skips it)
```

---

### Mode 09: Multi-Frame vs Single-Frame

#### Multi-Frame Responses

The ECU returns multiple records via multiple CAN frames, each with its own record index:

```
0904 multi-frame example (4 calibration IDs):

87 F1 10 49 04 01 33 32 39 32 A6    → record ① "3292"
87 F1 10 49 04 02 30 2D 31 30 95    → record ② "0-10"
87 F1 10 49 04 03 4B 30 2A 30 AD    → record ③ "K0*0"
87 F1 10 49 04 04 30 30 30 30 99    → record ④ "0000"

Per-frame structure: CAN ID(3) | PCI(1) | 49(mode) | 04(InfoType) | IDX(record) | DATA... | PAD(CAN padding)
                                                         ↑ extraSkip=1 skips     ↑ payload
```

Processing: `extractPerFramePayloads` extracts payloads per frame → `formatSingleRecord` formats → `joinToString("\n")` joins for display.

##### CAN + ATH1 Multi-Frame Reassembly

When ATH1 (CAN response header display) is enabled, ELM327 outputs ISO 15765-2 multi-frame responses as independent ASCII lines per frame:

```
0904 multi-frame ATH1 response (1 record):
7E8 10 13 49 04 01 33 33 33 39    ← FF (PCI=10, len=0x13)
7E8 21 32 30 2D 36 32 4C 36       ← CF (PCI=21, seq=1)
7E8 22 2A 30 30 30 30 31 00       ← CF (PCI=22, seq=2)
```

CF lines do not contain the OBD response header `49 04`, so the original `extractPerFrameRaw` would discard them entirely. `MultiFrameHandler.reassembleMultiFrame` performs reassembly at the ASCII level:

1. Scan each line for PCI token (high nibble = `1` → FF, = `2` → CF) — no fixed offset dependency
2. Strip CAN ID + PCI header, extract data hex tokens
3. Replace CF lines with OBD response header + frame sequence number (`49 04 01`, `49 04 02`, ...)
4. Preserve the original OBD header on FF lines

```
After reassembly (`\r`-separated):
49 04 01 33 33 33 39          ← FF line, original OBD header preserved
49 04 02 32 30 2D 36 32 4C 36  ← CF line, replaced with 49 04 02 header
49 04 03 2A 30 30 30 30 31 00  ← CF line, replaced with 49 04 03 header
```

After reassembly, every line is a standard OBD response line, so `extractPerFrameRaw(headerBytes=3)` can extract each frame's payload normally.

**Trigger condition**: CAN protocol (ATDPN = 6–A) + ATH1 (`showResponseHeaders = true`, enabled by default).

**Affected InfoTypes**: `0902` (VIN), `0904` (Calibration ID), `0906` (CVN), `0908` (IUPR), `090A` (ECU Name).

##### ISO 15765-2 PCI Frame Format

| PCI Byte High Nibble | Type | Description |
| :--- | :--- | :--- |
| `0x1` | First Frame (FF) | Low nibble + next byte = total data length (12 bits) |
| `0x2` | Consecutive Frame (CF) | Low nibble = sequence number (0–15, rolling) |
| `0x3` | Flow Control (FC) | Sent by receiver to control transmission rate |

#### Single-Frame Responses (SAE J1979 Standard Format)

```
49 04 [count] [record1_16_byte_padded] [record2_16_byte_padded] ...
```

---

### Response Trailing Padding / Checksum Bytes

Some ECUs or protocols append extra bytes after the valid data, causing responses longer than expected:

| Command | Raw Response | Trailing Extra Bytes | Handling |
| :--- | :--- | :--- | :--- |
| **0101** | `41 01 01 00 00 00` | `00 00 00` (CAN 8-byte padding) | metadata.bytesCount=4 → trim to 4 bytes |
| **0103** | `41 03 01 00 CA` | `CA` (checksum) | metadata.bytesCount=2 → trim to 2 bytes |
| **0903** | `49 03 04 D4` | `D4` (checksum) | Take first byte value → `4` |
| **0905** | `49 05 01 D3` | `D3` (checksum) | Take first byte value → `1` |
| **020200** | `42 02 00 01 70 3B` | `3B` (checksum) | metadata.bytesCount=2 → trim to 2 bytes |
| **Bitmap types** | `41 00 BE 1F A8 13 xx` | `xx` (trailing padding) | Bitmap always takes first 4 bytes |

**Unified handling strategy**:

- **PID value types** (Mode 01/02): `parsePIDResponse` trims using `metadata.bytesCount`
- **Bitmap types** (0100/0120/0200/0900): 3 discovery methods uniformly use `copyOf(4)`
- **Mode 09 count types** (0903/0905): `formatInfoTypeResult` reads only `data[0]`
- **Mode 09 multi-record types** (0904/0906/090A): In multi-frame scenarios, per-frame payloads are formatted directly, no concatenation-then-trim

---

## 🚀 Build & Run

### Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- Gradle 8.9+

### Mirror Acceleration

`gradle-wrapper.properties` defaults to Tencent Cloud mirror:
```
https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip
```

### Build Steps

```bash
# 1. Clone the project
git clone <repo-url>
cd Free_OBD

# 2. Build Debug APK
./gradlew assembleDebug

# 3. Install to device
./gradlew installDebug
```

Or open the project directly in Android Studio and click the Run button.

---

## 📱 Usage Guide

### 🎮 Demo Mode (Recommended for first-time experience)

1. Open the app, toggle the **Demo** switch at the top
2. A **DEMO** badge appears in the title bar, indicating demo mode is active
3. Tap **Scan for Devices** — 4 simulated OBD adapters appear
4. Select one and tap **Connect** — instantly "connected"
5. Go to **Dashboard** — gauges automatically start showing simulated data
6. Go to **Live Data Explorer** — browse all Mode 01 PIDs
7. Go to **Freeze Frame Data** — view freeze frame bitmap and P0170 data
8. Go to **Diagnostic Trouble Codes** — view sample DTCs
9. Go to **Vehicle Information** — view sample VIN and ECU info
10. Go to **DTC Lookup** — browse and search DTC definitions offline
11. Enable **Debug Logging**, then go to **Debug Console** — view simulated AT command interaction

> Demo mode requires no Bluetooth permissions or hardware — all data is locally simulated.

### 1. Connecting a Real Adapter

- Make sure the **Demo** toggle is off
- Launch the app, tap **Scan for Devices**
- Grant Bluetooth permissions on first use
- Ensure the OBD adapter is plugged into the vehicle's OBD-II port and powered on
- Select your adapter from the device list (commonly named OBDII, ELM327, Vgate, etc.)
- **Protocol selection**: Default ATSP0 (auto-detect). For motorcycles with K-line, manually select ATSP3 (ISO 9141-2), ATSP4 (KWP 5Bd), or ATSP5 (KWP Fast)
- Expand **Advanced Options** to set ECU address, enable response header display, and enable debug logging
- After a successful connection, the **Connected** card shows voltage, adapter firmware version, and negotiated protocol

### 2. Dashboard

- After connecting, tap **Dashboard**
- Gauges start polling automatically — no need to manually tap Start
- Tap the **+** button in the top-right corner to add/remove gauges, up to 6
- The bottom bar Start/Stop controls polling

### 3. Live Data Explorer

- Tap **Live Data Explorer** to browse all Mode 01 PIDs
- Top 8 segment selectors (00–E0) switch between bitmap ranges
- Green block = ECU supported, tap to jump to the corresponding PID card
- Each PID card shows its real-time value

### 4. Freeze Frame Data

- Tap **Freeze Frame Data** to view snapshot data captured at the moment a fault triggered
- ⏮ ⏭ buttons in the top-right corner navigate between multiple frames
- Bitmap blocks + PID detail cards, consistent with Live Data Explorer

### 5. Reading DTCs

- Tap **Diagnostic Trouble Codes**
- All DTCs are loaded automatically; switch between Stored / Pending / Permanent tabs
- Tap a single DTC to view detailed information (code, description, category)
- Tap the trash icon to clear stored DTCs (confirmation required)

### 6. Vehicle Information

- Tap **Vehicle Information** — first sends `0900` to query the InfoType bitmap
- The top area displays the raw response hex string and support status blocks (green = supported, red = not supported)
- Each InfoType (VIN, Calibration ID, CVN, ECU Name, etc.) is displayed as an individual card:
  - ✅ + result content — ECU supports it and data was retrieved successfully
  - ✅ + error message — ECU supports it but retrieval failed (e.g., 7F negative response)
  - ❌ — ECU does not support this InfoType
- The refresh button in the top-right corner re-pulls all data

### 7. PID Detail Dialog

- PID/InfoType cards in each Explorer page (Live Data / Freeze Frame / Vehicle Info) are tappable
- Tapping opens a dialog showing full metadata from `pid_definitions.json`:
  - Description, unit, value range, byte count
  - **Formula** (displayed in monospace font, auto line-wrapped)

### 8. DTC Lookup

- Tap **DTC Lookup** to enter the DTC reference page
- Built-in DTC definitions, supports offline browsing
- **Search**: Enter a code or description keyword in the input field; 300ms debounced auto-query
- **Pagination**: Default 50 per page; dropdown supports 10/20/50/100/200/500 per page
- **Page Jump**: Enter a page number for quick navigation
- **Details**: Tap any entry to view its code, description, and category (POWERTRAIN / BODY / CHASSIS / NETWORK)

### 9. Debug Console

- In the Bluetooth scan page, expand **Advanced** options and enable **Enable Debug Logging**
- After connecting, tap **Debug Console** to enter the debug page
- **TX** (blue) = sent commands, **RX** (gray) = adapter responses, **ERR** (red) = errors
- Tap the **Save** button to choose a save path via the system file picker (SAF)
- The bottom **input field** supports manually entering AT/OBD commands and actually sending them to the ELM327 (returns unsupported prompt in Demo mode)
- Each re-connect clears the previous session's log

---

## 🔧 ELM327 Initialization Sequence

```
1. ATZ              Reset, clears previous session state
2. ATE0             Disable command echo
3. ATL0             Disable line feeds
4. AT+VERSION       Extended version info; auto-extracts crypt: challenge
4.5. AT+SETCRYPT    Auto-computes the key from the crypt: challenge and sends it
5. ATSPx            Protocol selection (ATSP0 = auto-detect)
6. ATH0/ATH1        Always sent: ATH0 = no headers, ATH1 = user enabled "Show Response Headers"
7. ATSH             ECU header address (optional, only sent if configured)
```

- **ATRV / ATI**: Not sent during the initialization sequence. After a successful connection, the ViewModel queries them separately for UI display (voltage + firmware version), avoiding redundant sends.
- **K-line protocols (ATSP3/4/5)**: ELM327 automatically performs 5-baud slow or fast init on the first OBD command
- **Non-critical step**: AT+SETCRYPT failure does not block the initialization flow (the adapter may not support the command)
- **Encryption**: The `crypt:` challenge value differs on every connection; the key is computed in real time using a reverse-engineered algorithm (see [AT+SETCRYPT.md](AT+SETCRYPT.md))

---

## ⚠️ Notes

1. **Permissions**: Android 12+ requires BLUETOOTH_SCAN + BLUETOOTH_CONNECT; Android < 12 requires BLUETOOTH + BLUETOOTH_ADMIN + ACCESS_FINE_LOCATION
2. **Adapter Quality**: Cheap ELM327 clones may have response delays. Built-in 100ms inter-command delay + 10s first-command timeout
3. **Vehicle Compatibility**: The set of supported PIDs varies greatly between vehicle models. The app auto-discovers and only displays available PIDs
4. **K-Line / Motorcycles**: Not all motorcycles support standard OBD-II PIDs. China IV and newer models generally do. Start with ATSP3 (ISO 9141-2); if that fails, try ATSP5 (KWP Fast) then ATSP4 (KWP 5Bd) in order
5. **CAN Protocol Vehicles**: Gasoline vehicles from 2008 onward and diesel vehicles from 2004 onward generally support CAN protocols (ATSP6/ATSP7)
6. **Demo Mode Limitations**: Simulated data is for experience only. Parameters like vehicle speed and RPM are randomly generated and do not represent real vehicle conditions
7. **Encryption Key**: Some cheap ELM327 clones require dynamic challenge recognition and key computation — no manual configuration needed. Standard ELM327 adapters automatically skip this step
8. **Negative Response (7F)**: When the ECU rejects an OBD request, it returns `7F [service] [code]` (e.g., `7F 09 11` means Mode 09 is not supported). The Debug Console displays a red ERR log. Common causes: vehicle does not support the mode, request conditions not met, security access denied

---

## 📄 License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full license text.

```
Copyright 2026 3badguys <chuiC456@163.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
