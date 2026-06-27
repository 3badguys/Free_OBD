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
| **协议自动/手动选择** | 支持 ATSP0-ATSP9 共 10 种 OBD 协议 |
| **ECU 地址配置** | 支持广播地址（0x7DF）和特定 ECU 地址 |
| **PID 自动发现** | 通过位图链轮询发现车辆支持的所有 PID |
| **实时数据仪表盘** | Canvas 仪表盘组件（指针 + 弧线 + 刻度），自动开始轮询，进入即显示 |
| **可定制仪表盘** | 15 种可选 PID，随时添加/移除仪表盘组件 |
| **故障码读取/清除** | Mode 03/07/0A 读取存储码、待定码、永久码，带详情对话框 |
| **故障码详情** | 内置 SAE J2012 故障码数据库（120+ 常见代码） |
| **冻结帧数据** | Mode 02 读取故障触发瞬间的数据快照 |
| **车辆信息** | Mode 09 读取 VIN 码、校准 ID、CVN 校验和 |
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
│   │   ├── ObdCommandQueue.kt  # 原始 ELM327 命令队列 + 响应解析
│   │   ├── ELM327Initializer.kt    # ATZ→ATE0→ATL0→ATSP→ATH1 初始化序列
│   │   ├── PIDBitmapParser.kt      # PID 位图解析（SAE J1979）
│   │   ├── DTCParser.kt           # DTC 故障码解析（SAE J2012）
│   │   └── MultiFrameHandler.kt   # ISO 15765-2 多帧拼接（VIN 等）
│   ├── mock/                    # Demo 模式
│   │   ├── MockBluetoothRepository.kt  # 模拟蓝牙扫描 + 连接
│   │   ├── MockOBDRepository.kt       # 模拟 OBD 数据引擎
│   │   └── DemoModeState.kt           # 全局真实/模拟仓库切换
│   └── repository/              # Repository 实现
│       ├── BluetoothRepositoryImpl.kt
│       └── OBDRepositoryImpl.kt
├── domain/
│   ├── model/                   # OBDData, DTC, VehicleInfo, PIDDefinition 等
│   ├── repository/              # BluetoothRepository, OBDRepository 接口
│   └── usecase/                 # ConnectBluetooth, ReadLiveData, ReadDTC 等
├── presentation/
│   ├── bluetooth/               # 蓝牙连接页面 + Demo 开关
│   ├── dashboard/               # 仪表盘页面 + GaugeWidget + PID 选择器
│   ├── dtc/                     # 故障码页面 + 详情对话框（Stored/Pending/Permanent 标签页）
│   ├── vehicle/                 # 车辆信息页面
│   ├── components/              # LoadingOverlay, ErrorBanner, StatusIndicator
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
- 可展开 **Advanced Options** 手动设置 ECU CAN 地址

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

---

## ⚠️ 注意事项

1. **权限**：Android 12+ 仅需蓝牙权限，Android < 12 需要蓝牙 + 位置权限
2. **适配器质量**：廉价 ELM327 克隆版可能存在响应延迟，应用内置了 100ms 命令间延迟
3. **车辆兼容性**：不同车型支持的 PID 集差异较大，应用会自动发现并只显示可用的 PID
4. **安全警告**：Mode 08 双向控制功能需谨慎使用，可能影响车辆运行
5. **CAN 协议车型**：2008 年以后的汽油车和 2004 年以后的柴油车普遍支持 CAN 协议（ATSP6/ATSP7）
6. **Demo 模式限制**：模拟数据仅供体验，车速和 RPM 等参数为随机生成，不代表真实车辆状态
