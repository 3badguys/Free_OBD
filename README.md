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
| **协议自动/手动选择** | 支持 ATSP0-ATSP9 共 10 种协议：CAN、K 线（ISO 9141-2 / KWP2000）、J1850 PWM/VPW |
| **K 线支持** | 摩托车 ECU 适配 — 自动 5-baud / fast init，K 线自动跳过 ATH1 |
| **加密握手 (AT+SETCRYPT)** | 自动识别并从 AT+VERSION 提取挑战值并计算密钥 |
| **电压检测** | ATRV 读取电压，低电压时弹窗警告 |
| **适配器固件信息** | ATI 版本号显示在 Connected 卡片上 |
| **ECU 地址配置** | 支持广播地址（0x7DF）和特定 ECU 地址 |
| **PID 自动发现** | SAE J1979 位图链轮询（0100-01C0, 0200-02C0 等） |
| **实时数据仪表盘** | Canvas 仪表盘组件（指针 + 弧线 + 刻度），自动开始轮询，进入即显示 |
| **可定制仪表盘** | 15 种可选 PID，随时添加/移除仪表盘组件 |
| **故障码读取/清除** | Mode 03/07/0A 读取存储码、待定码、永久码，带详情对话框 |
| **故障码详情** | 内置 SAE J2012 故障码数据库（120+ 常见代码） |
| **冻结帧数据** | Mode 02 读取故障触发瞬间的数据快照 |
| **车辆信息** | Mode 09 读取 VIN 码、校准 ID、CVN 校验和 |
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
│   │   ├── entity/              # DtcDefinitionEntity, PidMetadataEntity, VehicleProfileEntity
│   │   ├── dao/                 # DtcDefinitionDao, PidMetadataDao, VehicleProfileDao
│   │   ├── AppDatabase.kt      # 数据库单例
│   │   ├── DtcDefinitionSeeder.kt  # 故障码 CSV 数据填充
│   │   └── PidMetadataSeeder.kt    # PID 元数据 JSON 数据填充
│   ├── remote/                  # OBD 通信层
│   │   ├── ObdTransport.kt     # 传输层抽象接口（SPP/BLE）
│   │   ├── SppTransport.kt     # 经典蓝牙 RFCOMM 实现
│   │   ├── BleTransport.kt     # BLE GATT 实现（骨架）
│   │   ├── ObdCommandQueue.kt  # 原始 ELM327 命令队列 + Mutex 串行化
│   │   ├── ELM327Initializer.kt    # ATZ→ATE0→ATL0→ATRV→ATI→AT+VERSION→AT+SETCRYPT→ATSP→ATH1→ATSH
│   │   ├── YMOBDCrypto.kt          # 加密密钥生成算法（crypt: 挑战值 → SETCRYPT 密钥）
│   │   ├── PIDBitmapParser.kt      # PID 位图解析（SAE J1979）
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
│   ├── model/                   # OBDData, DTC, VehicleInfo, DebugLog, ProtocolInfo 等
│   ├── repository/              # BluetoothRepository, OBDRepository 接口
│   └── usecase/                 # ConnectBluetooth, ReadLiveData, ReadDTC, DiscoverPIDs, ReadVehicleInfo
├── presentation/
│   ├── bluetooth/               # 蓝牙连接页面 + Demo 开关 + Protocol/Advanced 配置
│   ├── dashboard/               # 仪表盘页面 + GaugeWidget + PID 选择器
│   ├── dtc/                     # 故障码页面 + 详情对话框（Stored/Pending/Permanent 标签页）
│   ├── vehicle/                 # 车辆信息页面
│   ├── debug/                   # 调试控制台页面（命令日志 + 手动 TX）
│   ├── components/              # 公共组件
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
5. 进入 **Live Data Dashboard** — 仪表盘自动开始显示模拟数据
6. 进入 **Diagnostic Trouble Codes** — 查看示例故障码
7. 进入 **Vehicle Information** — 查看示例 VIN 和 ECU 信息

> Demo 模式无需蓝牙权限，无需任何硬件，所有数据均为本地模拟。

### 1. 连接真实适配器

- 确保 **Demo** 开关关闭
- 启动应用，点击 **Scan for Devices**
- 首次使用需授予蓝牙权限
- 确保 OBD 适配器已插入车辆 OBD-II 接口并通电
- 在设备列表中选择你的适配器（通常名为 OBDII、ELM327、Vgate 等）
- **协议选择**：默认 ATSP0（自动检测）。摩托车 K 线建议手动选 ATSP3（ISO 9141-2）、ATSP4（KWP 快）或 ATSP5（KWP 慢）
- 展开 **Advanced Options** 可设置 ECU 地址、启用调试日志
- 连接成功后，**Connected** 卡片显示电压、适配器固件版本、协商协议

### 2. 查看实时数据

- 连接成功后，点击 **Live Data Dashboard**
- 仪表盘自动开始轮询，无需手动点击 Start
- 点击右上角 **+** 按钮添加/移除仪表盘（15 种 PID 可选）
- 底部栏可 Start/Stop 控制轮询

### 3. 读取故障码

- 点击 **Diagnostic Trouble Codes**
- 自动加载所有故障码，在 Stored / Pending / Permanent 标签页切换
- 点击单个故障码查看详细信息（严重程度、系统分类、建议）
- 点击垃圾桶图标清除存储的故障码（需确认）

### 4. 车辆信息

- 点击 **Vehicle Information** 自动读取 VIN 码和 ECU 校准信息

### 5. 调试控制台

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
4. ATRV             电压检测（非关键步骤）
5. ATI              固件版本（非关键）
6. AT+VERSION       扩展版本信息；自动提取 crypt: 挑战值
7. AT+SETCRYPT      根据 crypt: 挑战值自动计算密钥并发送
8. ATSPx            协议选择（ATSP0 = 自动检测）
9. ATH1             CAN 头（仅 CAN 协议发送，K 线 ATSP3/4/5 跳过）
10. ATSH            ECU 头地址（可选，配置了才发）
```

- **K 线协议（ATSP3/4/5）**：第 9 步 ATH1 跳过。ELM327 在首次 OBD 命令时自动执行 5-baud 慢速或快速 init
- **非关键步骤**：失败不会阻断初始化流程（适配器可能不支持该命令）
- **加密**：`crypt:` 挑战值每次连接都不同，密钥通过逆向算法实时计算（详见 [AT+SETCRYPT.md](AT+SETCRYPT.md)）

---

## ⚠️ 注意事项

1. **权限**：Android 12+ 需要 BLUETOOTH_SCAN + BLUETOOTH_CONNECT，Android < 12 需要 BLUETOOTH + BLUETOOTH_ADMIN + ACCESS_FINE_LOCATION
2. **适配器质量**：廉价 ELM327 克隆版可能存在响应延迟。内置 100ms 命令间延迟 + 10s 首命令超时
3. **车辆兼容性**：不同车型支持的 PID 集差异较大，应用会自动发现并只显示可用的 PID
4. **K 线 / 摩托车**：并非所有摩托车都支持标准 OBD-II PID。国四及更新车型一般支持，建议先试 ATSP3
5. **CAN 协议车型**：2008 年以后的汽油车和 2004 年以后的柴油车普遍支持 CAN 协议（ATSP6/ATSP7）
6. **Demo 模式限制**：模拟数据仅供体验，车速和 RPM 等参数为随机生成，不代表真实车辆状态
7. **加密密钥**：个别廉价 ELM327 克隆版需要动态识别并计算密钥，无需手动配置。标准 ELM327 适配器自动跳过此步骤
