# Free OBD — 开源 Android OBD-II 汽车诊断应用

<div align="center">

🚗🔧📊

*一款基于 Kotlin + Jetpack Compose 的专业级 OBD-II 汽车诊断工具*

</div>

---

## 📋 项目简介

Free OBD 是一款功能完善的 Android OBD-II 诊断应用，支持通过蓝牙（经典 SPP + BLE）连接 ELM327 适配器，读取车辆实时数据、诊断故障码（DTC）、冻结帧数据和车辆信息。

**内置 Demo 模式**，无需任何硬件即可体验全部功能。

### ✨ 核心功能

| 功能 | 说明 |
| :--- | :--- |
| **🎮 Demo 模式** | 内置模拟数据引擎，无需 OBD 适配器即可完整体验所有功能 |
| **蓝牙设备扫描** | 同时扫描经典蓝牙（SPP）和低功耗蓝牙（BLE）OBD 适配器 |
| **协议自动/手动选择** | 支持 ELM327 全部 13 种协议：CAN、K 线（ISO 9141-2 / KWP2000）、J1850 PWM/VPW、J1939 等 |
| **K 线支持** | 摩托车 ECU 适配 — 自动 5-baud / fast init |
| **加密握手 (AT+SETCRYPT)** | 自动识别并从 AT+VERSION 提取挑战值并计算密钥 |
| **电压检测** | ATRV 读取电压，低电压时弹窗警告 |
| **适配器固件信息** | ATI 版本号显示在 Connected 卡片上 |
| **ECU 地址配置** | 支持广播地址（0x7DF）和特定 ECU 地址 |
| **PID 位图发现** | Mode 01/02/09 三位图逐段查询，支持方块可视化 + 点击跳转 |
| **实时仪表盘** | Canvas 仪表盘（渐变弧线 + 指针阴影），最多 6 个，自动轮询 |
| **Live Data Explorer** | 按段浏览全部 Mode 01 PID 及其实时数值 |
| **冻结帧数据** | Mode 02 位图发现 + 逐 PID 拉取，支持多帧前后切换 |
| **故障码读取/清除** | Mode 03/07/0A 三标签页，清码 Mode 04 需确认对话框 |
| **故障码详情** | 内置 SAE J2012 故障码数据库（从预置 dtc_codes.db 加载） |
| **DTC 离线查询** | 故障码参考页面 — 分页浏览、搜索、每页条数可选、页码跳转 |
| **PID 详情弹窗** | 点击任意 PID 卡片弹出完整元数据（描述、单位、范围、公式），三个 Explorer 页面统一复用 |
| **车辆信息** | Mode 09 位图发现 + 逐项拉取 VIN、校准 ID、CVN、ECU 名称 |
| **调试控制台** | 实时查看 TX/RX 命令日志，支持 SAF 保存路径选择 + 手动 TX 输入 |
| **运行时权限** | Android 12+ / 12 以下自适应权限请求 |

---

## 🛠️ 技术栈

| 组件 | 技术 | 版本 |
| :--- | :--- | :--- |
| **语言** | Kotlin | 2.3.0 |
| **UI** | Jetpack Compose + Material 3 | BOM 2024.10.00 |
| **架构** | MVVM + Clean Architecture | — |
| **异步** | Kotlin Coroutines + Flow | 1.8.1 |
| **数据库** | Room | 2.7.0 |
| **依赖注入** | Koin | 3.5.6 |
| **OBD 协议** | kotlin-obd-api (eltonvs) | 1.4.1 |
| **KSP** | KSP2 | 2.3.4 |
| **最低 SDK** | API 24（Android 7.0） | — |
| **目标 SDK** | API 34（Android 14） | — |

---

## 📁 项目结构

```
app/src/main/java/com/freeobd/app/
├── data/
│   ├── local/                   # Room 数据库
│   │   ├── entity/              # DtcEntity, PidMetadataEntity, VehicleProfileEntity
│   │   ├── dao/                 # DtcDao, PidMetadataDao, VehicleProfileDao
│   │   ├── AppDatabase.kt      # 数据库单例
│   │   ├── DtcSeeder.kt         # 故障码预置数据库 (dtc_codes.db) 填充
│   │   └── PidMetadataSeeder.kt    # PID 元数据 JSON (pid_definitions.json, 186 条) 填充
│   ├── remote/                  # OBD 通信层
│   │   ├── ObdTransport.kt     # 传输层抽象接口（SPP/BLE）
│   │   ├── SppTransport.kt     # 经典蓝牙 RFCOMM 实现
│   │   ├── BleTransport.kt     # BLE GATT 实现（骨架）
│   │   ├── ObdCommandQueue.kt  # 原始 ELM327 命令队列 + Mutex 串行化
│   │   ├── ELM327Initializer.kt    # ATZ→ATE0→ATL0→AT+VERSION→(SETCRYPT)→ATSP→ATH0/ATH1→ATSH
│   │   ├── YMOBDCrypto.kt          # 加密密钥生成算法（crypt: 挑战值 → SETCRYPT 密钥）
│   │   ├── PIDBitmapParser.kt      # 位图解析 parsePidBitmap()（Mode 01/02/09 三个 Explorer 共用）
│   │   ├── DTCParser.kt           # DTC 故障码解析（SAE J2012）
│   │   ├── MultiFrameHandler.kt   # ISO 15765-2 多帧拼接（VIN 等）
│   │   └── DebugLogger.kt         # 调试日志记录器（内存缓冲区）
│   ├── mock/                    # Demo 模式
│   │   ├── MockBluetoothRepository.kt  # 模拟蓝牙扫描 + 连接
│   │   ├── MockOBDRepository.kt       # 模拟 OBD 数据引擎（含调试日志）
│   │   └── DemoModeState.kt           # 全局真实/模拟仓库切换
│   └── repository/              # Repository 实现
│       ├── BluetoothRepositoryImpl.kt
│       └── OBDRepositoryImpl.kt
├── domain/
│   ├── model/                   # OBDData, DTC, VehicleInfo, ProtocolInfo, Discovery 等
│   ├── repository/              # BluetoothRepository, OBDRepository 接口
│   └── usecase/                 # ConnectBluetooth, ReadLiveData, ReadDTC
├── presentation/
│   ├── bluetooth/               # 蓝牙连接页面 + Demo 开关 + Protocol/Advanced 配置
│   ├── dashboard/               # 仪表盘页面（最多 6 个） + GaugeWidget + PID 选择器
│   ├── livedata/                # Live Data Explorer — Mode 01 位图 + PID 浏览
│   ├── freezeframe/             # Freeze Frame — Mode 02 位图 + 多帧导航
│   ├── dtc/                     # 故障码页面 + 详情对话框（Stored/Pending/Permanent）
│   ├── dtc_lookup/              # DTC 离线查询页面 — 分页浏览、搜索、页数/页码控制
│   ├── vehicle/                 # 车辆信息 — Mode 09 位图 + InfoType 卡片
│   ├── debug/                   # 调试控制台页面（命令日志 + 手动 TX）
│   ├── components/              # 共享组件（SupportBlockGrid, DetailCard, PidDetailDialog, SupportLegend）
│   ├── theme/                   # 深色主题配色（汽车仪表盘风格）
│   └── navigation/              # NavRoutes + AppNavHost
├── di/
│   └── AppModule.kt            # Koin DI 模块
└── utils/
    ├── ByteUtils.kt             # 字节/十六进制工具
    ├── CoroutineUtils.kt        # 协程扩展（超时、重试、节流）
    └── Extensions.kt            # 通用 Kotlin 扩展
```

---

## 📡 命令发送方法

`ObdCommandQueue` 提供三个命令入口，职责不同，避免混用：

| 方法 | 用途 | 7F 检测 | 日志行为 | 调用者 |
| :--- | :--- | :--- | :--- | :--- |
| `sendRaw` | AT 命令（`ATZ`、`ATRV`、`ATSP` 等）<br>ELM327 初始化序列 | ❌ 不检测 | `DebugLogger.enabled` 时记录 | `ELM327Initializer`、<br>`getProtocolInfo`、<br>`readVoltage`、`readAdapterInfo` |
| `sendObdCommand` | OBD 模式命令（`010C`、`03`、`0902` 等）<br>所有 01-0A 模式请求 | ✅ 检测 `7F xx yy`<br>命中返回 failure | 同上 | `discoverLiveDataPIDs`、`discoverFreezeFramePIDs`、<br>`discoverVehicleInfoTypes`、`readPID`、<br>`readDTCsFromMode`、`clearDTCs`、<br>`readFreezeFramePID`、`readLiveDataPID` 等 |
| `sendRawCommand`<br>（Repository 层） | Debug Console 手动 TX 输入<br>自动区分 AT / OBD 路由 | ✅ OBD 命令走<br>`sendObdCommand` | **始终记录**<br>不受 `DebugLogger.enabled` 影响 | `DebugConsoleScreen`<br>手动输入框 |

> **原则**：发送 01-0A 模式的 OBD 请求必须用 `sendObdCommand`，否则 7F 负响应会被当作"无数据"静默忽略。

### 7F 负响应码

ECU 拒绝 OBD 请求时返回 `7F [service] [code]`，常见错误码：

| Code | 含义 |
| :--- | :--- |
| `11` | serviceNotSupported |
| `12` | subFunctionNotSupported |
| `22` | conditionsNotCorrect |
| `31` | requestOutOfRange |
| `78` | responsePending |

---

## 📐 OBD 响应数据格式与解析


### extraSkip 机制

`extractFromDecoded` 标准跳过 2 字节（mode 响应 + 1 个子字节），`extraSkip` 用于声明额外跳过的字节：

| 场景 | extraSkip | 跳过内容 | 备注 |
| :--- | :--- | :--- | :--- |
| **Mode 01 / Mode 09 CAN** | `0` | mode + PID / InfoType | 无额外字节 |
| **Mode 02 非 CAN** | `1` | mode + PID + **帧号回显** | 帧号作为数据字节回显，需跳过 |
| **Mode 09 非 CAN（计数类）** | `0` | mode + InfoType | `0901`, `0903`, `0905`, `0909` — 无消息计数 |
| **Mode 09 非 CAN（数据类）** | `1` | mode + InfoType + **消息计数** | `0900`, `0902`, `0904`, `0906`, `0908`, `090A`, `090B` |
| **Mode 09 多帧多记录** | `1` | mode + InfoType + **记录索引** | `0904`, `0906`, `090A` 的每条帧 |

> `extraSkip = 0` 为默认值，所有调用处必须显式声明额外跳过的字节数，不在提取层做隐式协议推断。

---

### Mode 02：CAN vs 非 CAN 帧号回显

Mode 02 响应格式为 `42 PID FRAME [data]`，其中 `FRAME` 是冻结帧编号（0x00–0xFF）。

| 协议 | 帧号位置 | extraSkip | 原因 |
| :--- | :--- | :--- | :--- |
| **CAN**（协议 6–A） | CAN 多帧协议层处理 | **0** | 帧号由 ISO 15765-2 流控帧携带，不在 OBD 数据区重复 |
| **非 CAN**（协议 1–5） | OBD 数据区首字节 | **1** | K 线 / J1850 无多帧协议层，帧号作为独立数据字节回显 |

示例 — 查询 Mode 02 PID 02 帧 00（命令 `020200`）：

```
CAN     响应: 42 02 00 01 70 3B
              ↑↑ ↑↑      ↑↑↑↑
              md PID      data + padding
              (帧号 00 由 CAN 层处理，不在 OBD 数据中)

非 CAN  响应: 42 02 00 01 70 C3
              ↑↑ ↑↑ ↑↑   ↑↑↑↑
              md PID FRM  data + padding
              (帧号 00 作为数据字节回显，extraSkip=1 跳过)
```

---

### Mode 09 InfoType 04/06/0A：多帧 vs 单帧

#### 多帧响应

ECU 用多条 CAN 帧返回多个记录，每帧带自己的记录索引：

```
0904 多帧示例（4 个校准 ID）：

87 F1 10 49 04 01 33 32 39 32 A6    → 记录 ① "3292"
87 F1 10 49 04 02 30 2D 31 30 95    → 记录 ② "0-10"
87 F1 10 49 04 03 4B 30 2A 30 AD    → 记录 ③ "K0*0"
87 F1 10 49 04 04 30 30 30 30 99    → 记录 ④ "0000"

每帧结构: CAN ID(3) | PCI(1) | 49(mode) | 04(InfoType) | IDX(record) | DATA... | PAD(CAN填充)
                                                 ↑ extraSkip=1 跳过     ↑ payload
```

处理方式：`extractPerFramePayloads` 逐帧提取 payload → `formatSingleRecord` 格式化 → `joinToString("\n")` 拼接显示。

#### 单帧响应（SAE J1979 标准格式）

```
49 04 [count] [record1_16字节补齐] [record2_16字节补齐] ...
```

---

### 响应尾部填充/校验字节

部分 ECU 或协议在有效数据后追加额外字节，导致数据比预期长：

| 命令 | 原始响应 | 尾部额外字节 | 处理方式 |
| :--- | :--- | :--- | :--- |
| **0101** | `41 01 01 00 00 00` | `00 00 00`（CAN 8 字节补齐） | metadata.bytesCount=4 → 裁剪到 4 字节 |
| **0103** | `41 03 01 00 CA` | `CA`（校验和） | metadata.bytesCount=2 → 裁剪到 2 字节 |
| **0903** | `49 03 04 D4` | `D4`（校验和） | 首字节取值 → `4` |
| **0905** | `49 05 01 D3` | `D3`（校验和） | 首字节取值 → `1` |
| **020200** | `42 02 00 01 70 3B` | `3B`（校验和） | metadata.bytesCount=2 → 裁剪到 2 字节 |
| **Bitmap 类** | `41 00 BE 1F A8 13 xx` | `xx`（尾部填充） | bitmap 固定取前 4 字节 |

**统一处理策略**：

- **PID 数值类**（Mode 01/02）：`parsePIDResponse` 用 `metadata.bytesCount` 裁剪
- **Bitmap 类**（0100/0120/0200/0900）：3 个 discovery 方法统一 `copyOf(4)`
- **Mode 09 计数值类**（0903/0905）：`formatInfoTypeResult` 只读 `data[0]`
- **Mode 09 多记录类**（0904/0906/090A）：多帧场景逐帧 payload 直接格式化，不拼接裁剪

---

## 🚀 构建与运行

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17
- Android SDK 34
- Gradle 8.9+

### 镜像加速

`gradle-wrapper.properties` 默认使用腾讯云镜像：
```
https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip
```

### 构建步骤

```bash
# 1. 克隆项目
git clone <repo-url>
cd Free_OBD

# 2. 构建 Debug APK
./gradlew assembleDebug

# 3. 安装到设备
./gradlew installDebug
```

或在 Android Studio 中直接打开项目，点击 Run 按钮。

---

## 📱 使用说明

### 🎮 Demo 模式（推荐首次体验）

1. 打开应用，点击顶部 **Demo** 开关
2. 标题栏出现 **DEMO** 标签，表示已进入演示模式
3. 点击 **Scan for Devices** — 出现 4 个模拟 OBD 适配器
4. 选择一个点击 **Connect** — 即时"连接"成功
5. 进入 **Dashboard** — 仪表盘自动开始显示模拟数据
6. 进入 **Live Data Explorer** — 浏览 Mode 01 所有 PID
7. 进入 **Freeze Frame Data** — 查看冻结帧位图和 P0170 数据
8. 进入 **Diagnostic Trouble Codes** — 查看示例故障码
9. 进入 **Vehicle Information** — 查看示例 VIN 和 ECU 信息
10. 进入 **DTC Lookup** — 离线浏览和搜索故障码定义
11. 开启 **Debug Logging** 后进入 **Debug Console** — 查看模拟 AT 命令交互

> Demo 模式无需蓝牙权限，无需任何硬件，所有数据均为本地模拟。

### 1. 连接真实适配器

- 确保 **Demo** 开关关闭
- 启动应用，点击 **Scan for Devices**
- 首次使用需授予蓝牙权限
- 确保 OBD 适配器已插入车辆 OBD-II 接口并通电
- 在设备列表中选择你的适配器（通常名为 OBDII、ELM327、Vgate 等）
- **协议选择**：默认 ATSP0（自动检测）。摩托车 K 线建议手动选 ATSP3（ISO 9141-2）、ATSP4（KWP 5Bd）或 ATSP5（KWP Fast）
- 展开 **Advanced Options** 可设置 ECU 地址、开启响应头显示以及启用调试日志
- 连接成功后，**Connected** 卡片显示电压、适配器固件版本、协商协议

### 2. 仪表盘（Dashboard）

- 连接成功后，点击 **Dashboard**
- 仪表盘自动开始轮询，无需手动点击 Start
- 点击右上角 **+** 按钮添加/移除仪表盘，最多 6 个
- 底部栏 Start/Stop 控制轮询

### 3. Live Data Explorer

- 点击 **Live Data Explorer** 浏览 Mode 01 全部 PID
- 顶部 8 个段选择器（00–E0）切换位图范围
- 绿色方块 = ECU 支持，点击跳转到对应 PID 卡片
- 每个 PID 卡片显示实时数值

### 4. 冻结帧数据

- 点击 **Freeze Frame Data** 查看故障触发瞬间的快照数据
- 右上角 ⏮ ⏭ 按钮前后切换多帧
- 位图方块 + PID 详情卡片，与 Live Data Explorer 一致

### 5. 读取故障码

- 点击 **Diagnostic Trouble Codes**
- 自动加载所有故障码，在 Stored / Pending / Permanent 标签页切换
- 点击单个故障码查看详细信息（代码、描述、分类）
- 点击垃圾桶图标清除存储的故障码（需确认）

### 6. 车辆信息

- 点击 **Vehicle Information** 首先发送 `0900` 查询 InfoType 位图
- 顶部显示原始响应十六进制串和支持状态方块（绿 = 支持，红 = 不支持）
- 每个 InfoType（VIN、校准 ID、CVN、ECU 名称等）显示独立卡片：
  - ✅ + 结果内容 —— ECU 支持且数据获取成功
  - ✅ + 错误信息 —— ECU 支持但获取失败（如 7F 负响应）
  - ❌ —— ECU 不支持该 InfoType
- 右上角刷新按钮可重新拉取全部数据

### 7. PID 详情弹窗

- 各 Explorer 页面（Live Data / Freeze Frame / Vehicle Info）中的 PID/InfoType 卡片均支持点击
- 弹出对话框显示来自 `pid_definitions.json` 的完整元数据：
  - 描述、单位、数值范围、字节数
  - **公式**（等宽字体展示，自动处理换行）

### 8. DTC 查询

- 点击 **DTC Lookup** 进入故障码参考页面
- 内置故障码定义，支持离线浏览
- **搜索**：输入框输入代码或描述关键词，300ms 防抖自动查询
- **分页**：默认每页 50 条，下拉菜单支持 10/20/50/100/200/500 条切换
- **跳页**：输入页码快速跳转
- **详情**：点击任意条目弹出代码、描述、分类（POWERTRAIN / BODY / CHASSIS / NETWORK）

### 9. 调试控制台

- 在蓝牙扫描界面的 **Advanced** 选项中开启 **Enable Debug Logging**
- 连接成功后，点击 **Debug Console** 进入调试页面
- **TX**（蓝）= 发送的命令，**RX**（灰）= 适配器返回的响应，**ERR**（红）= 错误
- 点击 **保存** 按钮通过系统文件选择器（SAF）选择保存路径
- 底部 **输入框** 支持手动输入 AT/OBD 命令并实际发送给 ELM327（Demo 模式下返回不支持提示）
- 每次重新 Connect 会清空上次会话的日志

---

## 🔧 ELM327 初始化序列

```
1. ATZ              复位，清除前一个会话状态
2. ATE0             关闭命令回显
3. ATL0             关闭换行符
4. AT+VERSION       扩展版本信息；自动提取 crypt: 挑战值
4.5. AT+SETCRYPT    根据 crypt: 挑战值自动计算密钥并发送
5. ATSPx            协议选择（ATSP0 = 自动检测）
6. ATH0/ATH1        始终发送：ATH0 = 无 header（默认），ATH1 = 用户开启 "Show Response Headers"
7. ATSH             ECU 头地址（可选，配置了才发）
```

- **ATRV / ATI**：不在初始化序列中发送。连接成功后由 ViewModel 单独查询用于 UI 显示（电压 + 固件版本），避免重复发送。
- **K 线协议（ATSP3/4/5）**：ELM327 在首次 OBD 命令时自动执行 5‑baud 慢速或快速 init
- **非关键步骤**：AT+SETCRYPT 失败不会阻断初始化流程（适配器可能不支持该命令）
- **加密**：`crypt:` 挑战值每次连接都不同，密钥通过逆向算法实时计算（详见 [AT+SETCRYPT.md](AT+SETCRYPT.md)）

---

## ⚠️ 注意事项

1. **权限**：Android 12+ 需要 BLUETOOTH_SCAN + BLUETOOTH_CONNECT，Android < 12 需要 BLUETOOTH + BLUETOOTH_ADMIN + ACCESS_FINE_LOCATION
2. **适配器质量**：廉价 ELM327 克隆版可能存在响应延迟。内置 100ms 命令间延迟 + 10s 首命令超时
3. **车辆兼容性**：不同车型支持的 PID 集差异较大，应用会自动发现并只显示可用的 PID
4. **K 线 / 摩托车**：并非所有摩托车都支持标准 OBD-II PID。国四及更新车型一般支持，建议先试 ATSP3（ISO 9141-2）。如果不行，依次尝试 ATSP5（KWP Fast）和 ATSP4（KWP 5Bd）
5. **CAN 协议车型**：2008 年以后的汽油车和 2004 年以后的柴油车普遍支持 CAN 协议（ATSP6/ATSP7）
6. **Demo 模式限制**：模拟数据仅供体验，车速和 RPM 等参数为随机生成，不代表真实车辆状态
7. **加密密钥**：个别廉价 ELM327 克隆版需要动态识别并计算密钥，无需手动配置。标准 ELM327 适配器自动跳过此步骤
8. **负响应 (7F)**：ECU 拒绝 OBD 请求时返回 `7F [service] [code]`（如 `7F 09 11` 表示 Mode 09 不支持），Debug Console 会显示红色 ERR 日志。常见原因：车辆不支持该模式、请求条件不满足、安全访问拒绝
