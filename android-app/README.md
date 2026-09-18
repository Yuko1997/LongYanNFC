# LongYanNFC Android App

## 项目简介

基于微信小程序解包数据重构的原生Android应用。原小程序是LongYanNFC读卡器硬件的配套软件，通过BLE与硬件通信，支持Mifare/NTAG卡片读取、Amiibo数据管理、密钥破解等功能。

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI框架 | Jetpack Compose (Material 3) |
| 架构 | MVVM + Repository + Hilt |
| BLE通信 | Android BluetoothLe API |
| 网络 | Retrofit + OkHttp |
| 数据库 | Room |
| 图片加载 | Coil |

## 功能模块

### 核心功能
- ✅ BLE设备扫描与连接
- ✅ NFC卡片检测（Mifare Classic/Ultralight/NTAG）
- ✅ 卡片数据读取
- ✅ Amiibo数据下载与写入
- ✅ 十六进制编辑器
- ✅ 数据对比工具
- ✅ 密钥管理
- ✅ 卡片漏洞检测
- ✅ DFU固件更新

### 攻击算法
- ✅ Crypto1流密码实现
- ✅ 嵌套攻击（Nested Attack）
- ✅ 暗面攻击（Darkside Attack）
- ✅ 静态嵌套攻击（Static Nested）
- ✅ 云端暴力破解

### 云服务
- ✅ Amiibo数据库同步
- ✅ 密钥云端破解
- ✅ 用户认证
- ✅ VIP管理

## 项目结构

```
android-app/
├── app/
│   ├── build.gradle.kts          # 应用构建配置
│   └── src/main/
│       ├── AndroidManifest.xml   # 应用清单
│       ├── java/com/longyannfc/app/
│       │   ├── LongYanApp.kt     # Application类
│       │   ├── MainActivity.kt   # 主Activity
│       │   ├── ui/
│       │   │   ├── Navigation.kt # 导航配置
│       │   │   ├── theme/        # 主题配置
│       │   │   └── screens/      # 所有屏幕
│       │   ├── data/
│       │   │   ├── ble/          # BLE通信层
│       │   │   ├── remote/       # 云服务API
│       │   │   ├── local/        # 本地数据库
│       │   │   └── repository/   # 数据仓库
│       │   ├── di/               # 依赖注入
│       │   └── utils/            # 工具类
│       └── res/                  # 资源文件
├── build.gradle.kts              # 项目构建配置
├── settings.gradle.kts           # 项目设置
└── gradle.properties             # Gradle属性
```

## 核心算法还原

### Crypto1 流密码
- 48位LFSR状态机
- 非线性滤波函数
- 前向/反向密钥流生成
- PRNG后继函数
- 扩展表（mfkey32攻击）

### Mifare协议
- Mifare Classic 1K/4K (16扇区×4块×16字节)
- Mifare Ultralight (16页×4字节)
- NTAG213/215/216 (192/168/231字节用户存储)
- UID卡后门操作

### 攻击模式
| 攻击类型 | 描述 |
|---------|------|
| Nested Attack | 已知密钥→未知密钥恢复 |
| Darkside Attack | 弱PRNG漏洞利用 |
| Static Nested | Gen3/Gen4卡片静态nonce攻击 |
| Brute Force | 云端字典攻击 |

## 使用说明

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK 34
- Gradle 8.2

### 构建步骤

1. 克隆项目
```bash
git clone <repository-url>
cd LongYanNFC/android-app
```

2. 使用Android Studio打开项目

3. 同步Gradle依赖

4. 运行应用

### 配置说明

1. **BLE UUID配置**：在 `BleManager.kt` 中修改实际的设备UUID
2. **云服务地址**：在 `AppModule.kt` 中配置API基础URL
3. **权限**：应用会自动请求BLE和位置权限

## 注意事项

- 本项目仅用于学习和研究目的
- 请遵守相关法律法规，不要用于非法用途
- 云服务API地址可能需要根据厂商更新进行调整
- BLE UUID需要根据实际硬件进行调整

## 许可证

本项目仅用于学习和研究目的，请遵守相关法律法规。

## 致谢

感谢原微信小程序开发者的辛勤工作，本项目是对其工作的学习和研究。
