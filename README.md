# MikuBox for Android

An Android universal proxy toolchain powered by **Mihomo**.  
*一款使用 Mihomo 的 Android 通用代理程式*


[![Platform](https://img.shields.io/badge/android-platform?style=flat&label=platform&labelColor=21262d&color=6e7681)](https://www.android.com) [![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)  
[![Releases](https://img.shields.io/github/v/release/Yuk1Org/MikuBox)](https://github.com/Yuk1Org/MikuBox/releases) [![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0) 


---

[![Banner](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuBoxForAndroid/main/image/uwu_banner.png)]()

## v0.2.3 连接与后台恢复

- 配置、DNS 和规则供应器准备完成后才接管设备网络，避免启动和恢复期间空转。
- 跟随系统选出的底层网络，避免连接后因枚举顺序误从 Wi-Fi 切换到蜂窝网络。
- VPN 使用对应的前台服务类型；主动停止同时取消恢复，连续恢复失败后退避并暂停。
- VPN 设置可申请电池优化豁免，并提供 ColorOS 后台权限说明与应用设置入口。

修复 gVisor 停止时未唤醒读取线程的问题，避免空闲 TUN 残留并继续接管流量。

详见 [v0.2.3 更新说明](docs/releases/v0.2.3.md)。厂商一键清理策略仍需真机确认；始终开启 VPN、后台权限和电池优化豁免分别由系统管理，应用无法阻止强行停止。

## 配置与连接管理

- 主页「＋」可创建、管理配置组；删除组后配置移回 Miku。导入内容进入当前组，编辑页面可修改归属。
- 路由设置按配置文件、策略提供开关，独立保存覆盖项，不改写订阅原始规则。
- 配置编辑器以分区卡片呈现字段，提供分流规则表单和可视化 / 完整 YAML 切换；可视化修改会重新排版并移除注释。
- 通知与主页共享出口信息，VPN 设置提供始终开启与电池优化入口。

开启代理更新的订阅在首次无配置、未连接时先直连获取，后续保留代理更新设置。

详见 [v0.2.1 更新说明](docs/releases/v0.2.1.md)。

## Screenshots / 截圖預覽

A preview of MikuBox themes.  
*MikuBox 主題預覽*

<details>
  <summary><b>Light Theme / 淺色主題 (Click to view / 点击查看)</b></summary>

  <br>
  
![Screenshot1](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuBoxForAndroid/main/image/uwu_screenshot_1.png)

![Screenshot2](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuBoxForAndroid/main/image/uwu_screenshot_2.png)

![Screenshot3](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuBoxForAndroid/main/image/uwu_screenshot_3.png)

![Screenshot4](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuBoxForAndroid/main/image/uwu_screenshot_4.png)

![Screenshot5](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuBoxForAndroid/main/image/uwu_screenshot_5.png)

</details>

<details>
  <summary><b>Night Theme / 深色主題 (Click to view / 点击查看)</b></summary>

  <br>

![Screenshot6](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuBoxForAndroid/main/image/uwu_screenshot_6.png)

![Screenshot7](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuBoxForAndroid/main/image/uwu_screenshot_7.png)

![Screenshot8](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuBoxForAndroid/main/image/uwu_screenshot_8.png)

![Screenshot9](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuBoxForAndroid/main/image/uwu_screenshot_9.png)

![Screenshot10](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuBoxForAndroid/main/image/uwu_screenshot_10.png)

</details>

---

## Supported Protocols / 支援的協議

MikuBox embeds [HSSkyBoy/Mihomo](https://github.com/HSSkyBoy/Mihomo/tree/Alpha) `Alpha` through a JNI bridge. Android owns the VPN TUN interface while Mihomo receives its file descriptor and parses the complete YAML configuration.
*MikuBox 整合了 Mihomo 的核心功能，支援多種代理協議*

| Protocol Category (協議分類) |Protocols Name (協議名稱) |
| :--- | :--- |
| **Standard/標準** | SOCKS5, HTTP(S), SSH |
| **Established/常見** | Shadowsocks, ShadowsocksR, VMess, VLESS, Trojan |
| **Advanced/擴展** | AnyTLS, ShadowTLS (Shadowsocks/Snell plugin), TUIC, Hysteria 1/2, WireGuard, Snell, Mieru |
| **Groups/策略組** | select, url-test, fallback, load-balance |

> [!NOTE]
> Protocol support tracks the bundled `HSSkyBoy/Mihomo` `Alpha` commit recorded in the Git submodule. ShadowTLS is available as a Shadowsocks or Snell transport/plugin, not as its own `type: shadowtls` proxy node.
> The Alpha branch has removed the legacy `relay` proxy-group; use a node's `dialer-proxy` option for chaining instead.
> *協議支援以內建的 Mihomo 核心版本為準.*

---

## Supported Subscription Formats / 支援的訂閱格式

MikuBox natively consumes Mihomo (Clash) configuration — nodes, policy groups and rules included.  
*MikuBox 原生支援 Mihomo (Clash) 配置，節點、策略組與分流規則皆可用*

* **Native Format (原生格式):** Mihomo / ClashMeta (Clash YAML).
* **Import (匯入方式):** subscription URL or local config file.  *訂閱連結或本地配置檔.*

> [!NOTE]
> 
> Unlike the sing-box edition (which only resolved nodes), the Clash edition keeps the whole config —
> proxy-groups and routing rules are honored; the app only overrides a few fields (TUN / DNS / controller).  
> *有別於 sing-box 版 (只解析節點), Clash 版保留整份配置 — 策略組與分流規則皆生效, App 僅覆寫少數欄位 (TUN / DNS / 控制器).*

---

## Building from source / 自行構建

**Toolchain requirements:**
- **JDK 21** — required exactly. The pinned Gradle 8.14.4's embedded Kotlin cannot parse newer JDKs (running on JDK 25 fails during settings script compilation with `IllegalArgumentException: 25`), and the Android plugins need 17+.
- **Android SDK** with platform 36, build-tools 36.1.0, NDK 29.0.13599879 and CMake 3.22.1 (`sdkmanager --install "ndk;29.0.13599879" "cmake;3.22.1"`).
- **Go 1.25** — the `core/mihomo-bridge` JNI library is compiled from Go by the `buildMihomoBridge` Gradle task, once per ABI.

```bash
git clone --recursive https://github.com/Yuk1Org/MikuBox.git   # submodules carry the mihomo core
cd MikuBox
./gradlew :app:assembleDebug          # debug APKs (one per ABI)
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleRelease        # release APKs (unsigned without keystore secrets)
```

Release signing reads `KEYSTORE_PATH` / `KEYSTORE_PASS` / `ALIAS_NAME` / `ALIAS_PASS` from `local.properties` or the environment; without them release builds are left unsigned.

---

## Statistics & Community / 統計 & 社群

| Downloads | Commit Activity | Telegram Channel | Telegram 中文频道 |
| :---: | :---: | :---: | :---: |
| [![GitHub All Releases](https://img.shields.io/github/downloads/Yuk1Org/MikuBox/total?label=downloads-total&logo=github&style=flat-square)](https://github.com/Yuk1Org/MikuBox/releases) | [![GitHub commit activity](https://img.shields.io/github/commit-activity/m/Yuk1Org/MikuBox?style=flat&logo=Github)](https://github.com/Yuk1Org/MikuBox/commits/main) | [![Telegram](https://img.shields.io/badge/Hatsune-2CA5E0?style=flat&logo=telegram&logoColor=white)](https://t.me/uwuowoumuchannel) |[![Telegram](https://img.shields.io/badge/NPCN-2CA5E0?style=flat&logo=telegram&logoColor=white)](https://t.me/np_nbcn) |

---

## Credits / 致謝

This project is built upon the great work of the following open-source communities:  
*該項目建立在以下開源項目的出色工作之上:*

**Core:**
- [HSSkyBoy/Mihomo](https://github.com/HSSkyBoy/Mihomo/tree/Alpha)
- [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo)

**Upstream project:**
- [HatsuneMikuUwU/MikuBoxForAndroid](https://github.com/HatsuneMikuUwU/MikuBoxForAndroid)

**Android UI:**
- [HatsuneMikuUwU/MikuRay](https://github.com/HatsuneMikuUwU/MikuRay)
- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [2dust/v2rayNG](https://github.com/2dust/v2rayNG)

**Web Dashboard:**
- [MetaCubeX/Yacd-meta](https://github.com/MetaCubeX/Yacd-meta)
