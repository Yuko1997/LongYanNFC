# LongYanNFC Android App

## 项目简介

LongYanNFC 读卡器的原生 Android 配套应用。通过 BLE 与硬件通信，支持 Mifare / NTAG 卡片读取、写入、十六进制编辑、密钥缓存与卡片安全侦测。

**本应用为纯本地化工具**：不含用户登录、不含任何服务器连接，所有数据仅保存在设备本地。

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose (Material 3) |
| 架构 | MVVM + Repository + Hilt |
| BLE 通信 | Android BluetoothLe API |
| 数据库 | Room |
| 测试 | JUnit + Compose UI Test |

## 功能模块

### 核心功能
- BLE 设备扫描与连接
- NFC 卡片检测（Mifare Classic / Ultralight / NTAG）
- 卡片数据读取与写入
- 十六进制编辑器（按扇区分组、块级编辑）
- 读卡历史管理与文件导出
- 本地密钥缓存管理
- 卡片安全侦测（Crypto1 分析 + 嵌套攻击）
- DFU 固件更新

### 攻击算法
- Crypto1 流密码实现
- 嵌套攻击（Nested Attack）
- 暗面攻击（Darkside Attack）
- 静态嵌套攻击（Static Nested）
- 本地字典暴力破解

## 项目结构

```
LongYanNFC/
├── app/
│   ├── build.gradle.kts           # 应用构建配置
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/longyannfc/app/
│       │   │   ├── MainActivity.kt
│       │   │   ├── ui/
│       │   │   │   ├── Navigation.kt        # 导航与底部栏
│       │   │   │   ├── components/          # 共享 UI 组件
│       │   │   │   ├── screens/              # 页面
│       │   │   │   └── theme/                # 设计系统（色彩/排版/间距/形状）
│       │   │   ├── data/
│       │   │   │   ├── ble/                  # BLE 通信层
│       │   │   │   ├── local/                # Room 数据库
│       │   │   │   └── repository/           # 数据仓库
│       │   │   ├── di/                       # 依赖注入
│       │   │   └── utils/                    # 工具类
│       │   └── res/                          # 资源文件
│       ├── test/                              # 单元测试
│       └── androidTest/                       # 设备端测试
├── docs/                        # 设计规范与分析文档
├── build.gradle.kts
└── settings.gradle.kts
```

## UI 设计体系

界面遵循 `docs/Android_高级感_UI_设计规范_Kotlin.md`，采用 Quiet Premium 风格：

- **语义色板** —— 浅/深两套独立映射，单一靛蓝强调色（`ui/theme/Color.kt`）
- **排版层级** —— 9 级文字样式，等宽样式用于十六进制字段（`ui/theme/Type.kt`）
- **间距节奏** —— 4dp 基础、8dp 主节奏（`ui/theme/Spacing.kt`）
- **圆角层级** —— 8 / 12 / 16 / 24dp（`ui/theme/Shape.kt`）
- **共享组件** —— 页面只消费 Token 与共享组件，不各自定义样式（`ui/components/`）

## 核心算法

### Crypto1 流密码
- 48 位 LFSR 状态机
- 非线性滤波函数
- 前向 / 反向密钥流生成
- PRNG 后继函数
- 扩展表（mfkey32 攻击）

### Mifare 协议
- Mifare Classic 1K / 4K（16 扇区 × 4 块 × 16 字节）
- Mifare Ultralight（16 页 × 4 字节）
- NTAG213 / 215 / 216（192 / 168 / 231 字节用户存储）
- UID 卡后门操作

### 攻击模式

| 攻击类型 | 描述 |
|---------|------|
| Nested Attack | 已知密钥 → 未知密钥恢复 |
| Darkside Attack | 弱 PRNG 漏洞利用 |
| Static Nested | Gen3 / Gen4 卡片静态 nonce 攻击 |
| Brute Force | 本地字典暴力破解 |

## 构建与运行

### 环境要求
- JDK 17
- Android SDK 34
- Gradle 8.2（由 Wrapper 自动下载）

### 构建

```bash
git clone https://github.com/Yuko1997/LongYanNFC.git
cd LongYanNFC
./gradlew assembleDebug
```

### 运行测试

```bash
./gradlew testDebugUnitTest              # 单元测试
./gradlew connectedDebugAndroidTest     # 设备端测试（需连接设备）
```

### 配置说明

1. **BLE UUID 配置**：在 `data/ble/DeviceProfile.kt` 中修改设备 UUID
2. **权限**：应用会自动申请 BLE 扫描/连接与位置权限
3. **本地数据**：读卡历史与密钥缓存存储于应用私有数据库，卸载即清除

## 注意事项

- 本项目仅用于学习和研究目的
- 请遵守相关法律法规，不要用于非法用途
- BLE UUID 需要根据实际硬件进行调整

## 许可证

本项目仅用于学习和研究目的，请遵守相关法律法规。

