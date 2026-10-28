# LongYanNFC Android App - 重构进度报告

## 项目概述

基于微信小程序解包数据重构的原生 Android 应用。通过 BLE 与 LongYanNFC 读卡器硬件通信，支持 Mifare / NTAG 卡片的读取、写入、编辑与安全侦测。

**定位：纯本地化工具。** 不含用户登录，不连接任何服务器，全部数据保存在设备本地。

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose (Material 3)
- **架构**：MVVM + Repository + Hilt
- **BLE**：Android BluetoothLe API
- **持久化**：Room

## 已完成模块

### 1. 项目基础设施
- [x] Gradle 配置（AGP 8.2.2 / Kotlin 1.9.22 / Compose BOM 2024.01.00）
- [x] AndroidManifest（BLE 与位置权限，**无 INTERNET 权限**）
- [x] Application 类与 Hilt 依赖注入
- [x] 边到边布局（edge-to-edge）

### 2. 设计系统
- [x] 语义色板：浅/深双套独立映射，单一靛蓝强调色
- [x] 9 级排版层级 + 等宽样式
- [x] 4/8dp 间距系统、5 档圆角层级
- [x] 共享组件：页面容器、底部导航、内容面、设置行、状态标签、空/错/加载态
- [x] 全量页面 UI 重构与状态补全（Loading / Empty / Error / Disabled）

### 3. BLE 通信层
- [x] BleManager —— 扫描、连接、数据收发
- [x] PN532 —— NFC 控制器命令封装
- [x] MifareProtocol —— Mifare Classic / Ultralight / NTAG 抽象
- [x] DfuHandler —— Nordic Secure DFU 协议

### 4. 加密与攻击算法
- [x] Crypto1 流密码完整实现
- [x] CRC32 校验
- [x] NestedAttack / DarksideAttack / StaticNestedAttack
- [x] 密钥恢复与本地字典暴力破解

### 5. 数据持久化
- [x] Room 数据库（v3）
- [x] CardDumpDao —— 读卡历史
- [x] KeyDao —— 密钥缓存
- [x] LogDao —— 操作日志
- [x] 显式 Migration（v2 → v3 移除 Amiibo 表，保留历史数据）

### 6. 页面
- [x] MainScreen —— 设备连接、卡片检测、读取
- [x] DetectionScreen —— 一键智能侦测（进度 / 阶段时间线 / 安全分析）
- [x] CardHistoryScreen —— 读卡历史、导出、删除
- [x] EditorScreen —— 十六进制编辑器（扇区分组、块级编辑）
- [x] WriteCardScreen —— 写卡（数据源选择、进度、结果）
- [x] KeysScreen —— 本地密钥管理
- [x] SettingsScreen —— 设置分组
- [x] DfuScreen —— 固件更新
- [x] LogScreen / HelpScreen / FunctionScreen 等辅助页

## 明确移除的功能

本应用定位为纯本地化工具，以下内容已从代码库中完整移除：

- **用户登录与账号体系** —— 无登录入口，APK 不申请 INTERNET 权限
- **远程服务器连接** —— 移除 Retrofit / OkHttp / CloudApiService 全部云端接口
- **Amiibo 功能** —— 移除页面、ViewModel、写卡实现、数据库表与图片加载依赖

## 待完善功能

### 高优先级
1. **BLE 实际通信** —— 需根据实际硬件 UUID 调整
2. **DFU 完整流程** —— 需真实设备验证 Nordic Secure DFU 上传
3. **Supercard 设备配置** —— 固件版本与电量信息暂无数据源

### 中优先级
4. **虚拟卡片（vCard）** —— 目前为占位页
5. **读写卡片独立页** —— 目前为占位页
6. **大屏适配** —— 尚未引入 `WindowSizeClass`，宽屏下单列布局偏窄

### 低优先级
7. **操作日志清理** —— 缺少日志条数上限与手动清理入口

## 项目结构

```
LongYanNFC/
├── app/src/main/java/com/longyannfc/app/
│   ├── ui/theme/        # 设计系统
│   ├── ui/components/   # 共享组件
│   ├── ui/screens/      # 页面
│   ├── data/ble/        # BLE 通信
│   ├── data/local/      # Room
│   ├── data/repository/ # 数据仓库
│   ├── di/              # 依赖注入
│   └── utils/           # 工具类
└── docs/                # 设计规范与分析文档
```

## 验证状态

| 项目 | 状态 |
|------|------|
| `assembleDebug` 构建 | 通过 |
| 单元测试 | 通过 |
| 设备端 instrumentation 测试 | 通过（6 项） |
| 真机验证 | Android 16，1080×2412 逐页核对 |
| 深色模式 | 已验证 |
| 字体放大 1.5x | 已验证，无裁切 |
| 数据库迁移 v2→v3 | 已验证，历史数据无损 |

## 注意事项

- 本项目仅用于学习和研究目的
- 请遵守相关法律法规，不要用于非法用途
- BLE UUID 需要根据实际硬件进行调整

