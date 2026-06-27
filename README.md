# Free OBD — 开源 Android OBD-II 汽车诊断应用

<div align="center">

🚗🔧📊

*一款基于 Kotlin + Jetpack Compose 的专业级 OBD-II 汽车诊断工具*

</div>

---

## 📋 项目简介

Free OBD 是一款功能完善的 Android OBD-II 诊断应用，支持通过蓝牙（经典 SPP + BLE）连接 ELM327 适配器，读取车辆实时数据、诊断故障码（DTC）、冻结帧数据和车辆信息。

### ✨ 核心功能

| 功能 | 说明 |
| :--- | :--- |
| **蓝牙设备扫描** | 同时扫描经典蓝牙（SPP）和低功耗蓝牙（BLE）OBD 适配器 |
| **协议自动/手动选择** | 支持 ATSP0-ATSP9 共 10 种 OBD 协议 |
| **ECU 地址配置** | 支持广播地址（0x7DF）和特定 ECU 地址 |
| **PID 自动发现** | 通过位图链轮询发现车辆支持的所有 PID |
| **实时数据仪表盘** | Canvas 绘制仪表盘组件，显示转速、车速、水温等 |
| **故障码读取/清除** | Mode 03/07/0A 读取存储码、待定码、永久码 |
| **故障码详情** | 内置 SAE J2012 故障码数据库（120+ 常见代码） |
| **冻结帧数据** | Mode 02 读取故障触发瞬间的数据快照 |
| **车辆信息** | Mode 09 读取 VIN 码、校准 ID、CVN 校验和 |

---

## 🛠️ 技术栈

| 组件 | 技术 |
| :--- | :--- |
| **语言** | Kotlin 2.0 |
| **UI** | Jetpack Compose + Material 3 |
| **架构** | MVVM + Clean Architecture |
| **异步** | Kotlin Coroutines + Flow |
| **数据库** | Room（故障码定义 + PID 元数据缓存） |
| **依赖注入** | Koin |
| **OBD 协议** | kotlin-obd-api (eltonvs) |
| **最低 SDK** | API 24（Android 7.0） |
| **目标 SDK** | API 34（Android 14） |

---

## 📁 项目结构

```
app/src/main/java/com/freeobd/app/
├── data/
│   ├── local/               # Room 数据库（实体、DAO、种子数据）
│   │   ├── entity/           # DtcDefinitionEntity, PidMetadataEntity, VehicleProfileEntity
│   │   ├── dao/              # DtcDefinitionDao, PidMetadataDao, VehicleProfileDao
│   │   └── AppDatabase.kt   # 数据库单例
│   ├── remote/               # OBD 通信层
│   │   ├── ObdTransport.kt  # 传输层抽象接口（SPP/BLE）
│   │   ├── SppTransport.kt  # 经典蓝牙 RFCOMM 实现
│   │   ├── BleTransport.kt  # BLE GATT 实现（骨架）
│   │   ├── ObdCommandQueue.kt      # 命令队列 + 速率限制
│   │   ├── ELM327Initializer.kt    # ELM327 初始化序列
│   │   ├── PIDBitmapParser.kt      # PID 位图解析
│   │   ├── DTCParser.kt           # DTC 故障码解析
│   │   └── MultiFrameHandler.kt   # ISO 15765-2 多帧拼接
│   └── repository/           # Repository 实现
│       ├── BluetoothRepositoryImpl.kt
│       └── OBDRepositoryImpl.kt
├── domain/
│   ├── model/                # 领域模型
│   │   ├── OBDData.kt       # PID 数据类型（数值/字符串/位图）
│   │   ├── DTC.kt           # 故障码模型
│   │   ├── VehicleInfo.kt   # 车辆信息模型
│   │   ├── PIDDefinition.kt # PID 元数据
│   │   └── BluetoothDeviceInfo.kt
│   ├── repository/           # Repository 接口
│   └── usecase/              # 用例
│       ├── ConnectBluetoothUseCase.kt
│       ├── ReadLiveDataUseCase.kt
│       ├── ReadDTCUseCase.kt
│       ├── DiscoverPIDsUseCase.kt
│       └── ReadVehicleInfoUseCase.kt
├── presentation/
│   ├── bluetooth/            # 蓝牙连接页面
│   ├── dashboard/            # 仪表盘页面 + GaugeWidget
│   ├── dtc/                  # 故障码页面 + 详情对话框
│   ├── vehicle/              # 车辆信息页面
│   ├── components/           # 可复用组件
│   ├── theme/                # 深色主题配色
│   └── navigation/           # 导航路由
├── di/
│   └── AppModule.kt         # Koin DI 模块
└── utils/
    ├── ByteUtils.kt          # 字节/十六进制工具
    ├── CoroutineUtils.kt     # 协程扩展
    └── Extensions.kt         # 通用扩展函数
```

---

## 🚀 构建与运行

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17
- Android SDK 34
- Gradle 8.5+

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

### 1. 连接适配器
- 启动应用，点击"Scan for Devices"
- 确保 OBD 适配器已插入车辆 OBD-II 接口并通电
- 在设备列表中选择你的适配器（通常名为 OBDII、ELM327、Vgate 等）
- 可展开"Advanced Options"手动设置 ECU CAN 地址

### 2. 查看实时数据
- 连接成功后，点击 **Live Data Dashboard**
- 点击右上角 **+** 按钮选择要显示的 PID 仪表盘
- 点击 **Start** 开始轮询数据

### 3. 读取故障码
- 点击 **Diagnostic Trouble Codes**
- 在 Stored / Pending / Permanent 标签页切换
- 点击单个故障码查看详细信息和建议
- 点击垃圾桶图标清除存储的故障码

### 4. 车辆信息
- 点击 **Vehicle Information** 读取 VIN 码和 ECU 校准信息

---

## ⚠️ 注意事项

1. **权限**: 首次使用需授予蓝牙和位置权限（Android 12+ 仅需蓝牙权限）
2. **适配器质量**: 廉价 ELM327 克隆版可能存在响应延迟，应用内置了 100ms 命令间延迟
3. **车辆兼容性**: 不同车型支持的 PID 集差异较大，应用会自动发现并只显示可用的 PID
4. **安全警告**: Mode 08 双向控制功能需谨慎使用，可能影响车辆运行
5. **CAN 协议车型**: 2008 年以后的汽油车和 2004 年以后的柴油车普遍支持 CAN 协议（ATSP6/ATSP7）
