# OBD 通信加密握手逆向分析记录

## 📋 背景

- **问题**：Free_OBD App 无法读取数据，而其他的一些 App 可以正常读取。
- **目标**：找出 其他App 能成功通信的关键差异，并实现同样的功能。

---

## 🔍 第一阶段：对比抓包分析

### 1.1 环境准备
- 使用 Android 开发者选项中的 **"启用蓝牙HCI信息收集日志"** 进行抓包。
- 导出日志：使用 `adb bugreport` 或直接拉取 `/data/misc/bluetooth/logs/btsnoop_hci.log`。
- 用 **Wireshark** 打开分析。

### 1.2 Wireshark 过滤方法
```wireshark
btl2cap
```
或搜索字符串 `AT+VERSION`、`AT+SETCRYPT`。

### 1.3 关键发现
对比 其他APP 成功抓包记录，发现关键差异：

| Free_OBD | 其他APP |
| :--- | :--- |
| 发送 `ATZ`, `ATE0`, `ATL0`, `ATSP0`, `010C` | 发送 `ATZ`, `AT+VERSION`, `AT+SETCRYPT...`, `ATE0`, `ATSPx`, `010C` |

**结论**：其他APP 在初始化时增加了一个加密握手命令，格式为：
```
AT+SETCRYPTXXXXXXXX
```

---

## 🔬 第二阶段：`AT+VERSION` 返回数据分析

### 2.1 典型返回
```
AT+VERSION
Shenzhen Yuming Electronics Co., Ltd.
version:V1.0.0
device type:B02-Z
device name:OBDII
device mac:27:5A:C0:29:AD:5D
interface:v2.1
cust id:NONE
crypt:844C10BB
```

### 2.2 核心发现
- `crypt:` 后的 8 位十六进制字符串**每次连接都不同**（动态挑战值）。
- `AT+SETCRYPT` 后面的密钥也**每次不同**，且随 `crypt` 变化。

### 2.3 收集的 9 组对应数据

| crypt | SETCRYPT |
| :--- | :--- |
| D19107CD | E0DDCD1D |
| 844C10BB | 80FFBF49 |
| 1C5EB142 | BDE8D73F |
| DB3974EF | 80FB7FD3 |
| F5368B08 | 8E8B1B3E |
| 45A8A8F6 | D05FFD6A |
| B244C3D3 | E03F3A0C |
| 01E7C18E | C9E91D7B |
| 58A12672 | 98FA72B8 |

---

## 🛠️ 第三阶段：反编译分析

### 3.1 提取 APK
```bash
adb shell pm list packages -3                           # 找到包名
adb shell pm path rocket.vehiclemgr.android.obd2        # 获取 APK 路径
adb pull /data/app/rocket.vehiclemgr.android.obd2-xxx/base.apk chekuang.apk
```

### 3.2 使用 JADX-GUI 搜索
搜索关键词：`SETCRYPT`、`crypt:`、`AT+VERSION`、`OBDII`、`B02-Z`。

**结果**：Java 层搜不到相关代码，说明核心逻辑在 **Native 层（.so 文件）**。

### 3.3 定位 .so 文件
解压 APK，进入 `lib/arm64-v8a/`，发现 **`libobd_logic.so`**（9.6 MB）是核心 OBD 库。

### 3.4 使用 Ghidra 分析
1. 安装 JDK 21：
   - 清华镜像下载地址：[https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/x64/windows/](https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/x64/windows/)
   - 下载 `OpenJDK21U-jdk_x64_windows_hotspot_21.0.x_xx.msi` 安装包。
2. 下载 Ghidra：
   - 官方 GitHub：[https://github.com/NationalSecurityAgency/ghidra/releases](https://github.com/NationalSecurityAgency/ghidra/releases)
   - 下载 `ghidra_<version>_PUBLIC_<date>.zip`，解压运行 `ghidraRun.bat`。
3. 创建 `Non-Shared Project`，导入 `libobd_logic.so`。
4. 分析完成后按 `Shift+F12` 搜索字符串，成功找到：
   ```
   s_AT+SETCRYPT%08X_0043662b
   ```

---

## 🔐 第四阶段：算法还原

### 4.1 定位关键函数
搜索引用 `AT+SETCRYPT%08X` 的位置，找到函数 `TryYMOBDUnlock`。

### 4.2 伪代码分析

```c
void TryYMOBDUnlock() {
    // 1. 发送 AT+VERSION
    send("AT+VERSION");
    
    // 2. 提取 crypt: 后面的挑战值
    char* crypt = strstr(response, "crypt:") + 6;
    
    // 3. 调用两个转换函数
    uVar3 = FUN_008d42b8(crypt);     // 十六进制字符串 → 整数
    uVar3 = FUN_008d4360(uVar3);     // 核心加密算法
    
    // 4. 生成并发送命令
    sprintf(cmd, "AT+SETCRYPT%08X", uVar3);
    send(cmd);
}
```

### 4.3 `FUN_008d42b8` —— 字符串转整数

```c
uint FUN_008d42b8(long param_1) {
    uint result = 0;
    for (int i = 0; i < 8 && param_1[i] != '\0'; i++) {
        result = (result << 4) | FUN_008ea474(param_1[i]);
    }
    return result;
}

int FUN_008ea474(byte c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return 0;
}
```

### 4.4 `FUN_008d4360` —— 核心加密算法（从汇编还原）

```kotlin
val MAGIC = 0x263d9a7e

fun generateKey(challenge: String): String {
    val input = challenge.toInt(16)
    val b0 = input and 0xFF
    val b1 = (input ushr 8) and 0xFF
    val b2 = (input ushr 16) and 0xFF
    val b3 = input ushr 24

    val part1 = (b2 ushr (b0 / 0x32)) or ((MAGIC shl (b3 / 0x0c)) xor (b1 shl 2))
    val part2 = (b0 ushr (b1 / 0x3f)) or ((MAGIC shl (b3 / 0x0b)) xor (b2 ushr 1))
    val part3 = (b0 ushr (b1 / 0x2e)) or ((MAGIC shl (b0 / 0x22)) xor b3)
    val part4 = (b1 shl (b3 / 0x23)) or ((MAGIC shl (b0 / 0x31)) and 0x98f)

    val result = ((part4 and 0xFF) shl 24) or
                 ((part3 and 0xFF) shl 16) or
                 ((part2 and 0xFF) shl 8) or
                 (part1 and 0xFF)

    return String.format("%08X", result and 0xFFFFFFFFL)
}
```

---

## ✅ 第五阶段：验证

先用 Python 在本地验证算法是否正确（推荐使用 Python 3 运行）：

```python
def generate_key(challenge):
    input_val = int(challenge, 16)
    b0 = input_val & 0xFF
    b1 = (input_val >> 8) & 0xFF
    b2 = (input_val >> 16) & 0xFF
    b3 = input_val >> 24
    magic = 0x263d9a7e

    part1 = (b2 >> (b0 // 0x32)) | ((magic << (b3 // 0x0c)) ^ (b1 << 2))
    part2 = (b0 >> (b1 // 0x3f)) | ((magic << (b3 // 0x0b)) ^ (b2 >> 1))
    part3 = (b0 >> (b1 // 0x2e)) | ((magic << (b0 // 0x22)) ^ b3)
    part4 = (b1 << (b3 // 0x23)) | ((magic << (b0 // 0x31)) & 0x98f)

    result = ((part4 & 0xFF) << 24) | ((part3 & 0xFF) << 16) | ((part2 & 0xFF) << 8) | (part1 & 0xFF)
    return f"{result & 0xFFFFFFFF:08X}"

test_data = [
    ("D19107CD", "E0DDCD1D"),
    ("844C10BB", "80FFBF49"),
    ("1C5EB142", "BDE8D73F"),
    ("DB3974EF", "80FB7FD3"),
    ("F5368B08", "8E8B1B3E"),
    ("45A8A8F6", "D05FFD6A"),
    ("B244C3D3", "E03F3A0C"),
    ("01E7C18E", "C9E91D7B"),
    ("58A12672", "98FA72B8"),
]

for crypt, expected in test_data:
    result = generate_key(crypt)
    status = "✅" if result == expected else "❌"
    print(f"{crypt} -> {result}  {status}")
```

使用 9 组真实数据验证，全部匹配：

```
D19107CD -> E0DDCD1D  ✅
844C10BB -> 80FFBF49  ✅
1C5EB142 -> BDE8D73F  ✅
DB3974EF -> 80FB7FD3  ✅
F5368B08 -> 8E8B1B3E  ✅
45A8A8F6 -> D05FFD6A  ✅
B244C3D3 -> E03F3A0C  ✅
01E7C18E -> C9E91D7B  ✅
58A12672 -> 98FA72B8  ✅
```

---

## 📊 工具清单

| 工具 | 用途 |
| :--- | :--- |
| Serial Bluetooth Terminal | 手动发送 AT 命令调试 |
| Wireshark | 分析蓝牙 HCI 抓包 |
| JADX-GUI | 反编译 APK（Java 层） |
| Ghidra | 反编译 Native 层 .so 文件 |
| ADB | 提取 APK、抓取日志 |
| Python | 验证算法 |

---

## 💡 经验总结

1. **协议差异**：部分摩托车使用 KWP2000（K-Line），而非标准 OBD-II（CAN）。
2. **加密握手**：BLE ELM327 模块可能需要 `AT+SETCRYPT` 加密握手。
3. **动态密钥**：`crypt:` 挑战值 + 专用算法 → `SETCRYPT` 密钥。
4. **Native 层**：核心算法往往在 `.so` 文件中，需用 Ghidra/IDA 分析。
5. **多条线索并行**：抓包、搜索、反编译、动态分析交叉验证效率更高。
