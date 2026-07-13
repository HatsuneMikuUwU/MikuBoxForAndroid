# MikuBox for Android

An Android universal proxy toolchain powered by **Mihomo**.  
*一款使用 Mihomo 的 Android 通用代理程式*


[![Platform](https://img.shields.io/badge/android-platform?style=flat&label=platform&labelColor=21262d&color=6e7681)](https://www.android.com) [![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)  
[![Releases](https://img.shields.io/github/v/release/HatsuneMikuUwU/MikuBoxForAndroid)](https://github.com/HatsuneMikuUwU/MikuBoxForAndroid/releases) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0) 


---

[![Banner](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuBoxForAndroid/main/image/uwu_banner.png)]()

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

## Statistics & Community / 統計 & 社群

| Downloads | Commit Activity | Telegram Channel | Telegram 中文频道 |
| :---: | :---: | :---: | :---: |
| [![GitHub All Releases](https://img.shields.io/github/downloads/HatsuneMikuUwU/MikuBoxForAndroid/total?label=downloads-total&logo=github&style=flat-square)](https://github.com/HatsuneMikuUwU/MikuBoxForAndroid/releases) | [![GitHub commit activity](https://img.shields.io/github/commit-activity/m/HatsuneMikuUwU/MikuBoxForAndroid?style=flat&logo=Github)](https://github.com/HatsuneMikuUwU/MikuBoxForAndroid/commits/main) | [![Telegram](https://img.shields.io/badge/Hatsune-2CA5E0?style=flat&logo=telegram&logoColor=white)](https://t.me/uwuowoumuchannel) |[![Telegram](https://img.shields.io/badge/NPCN-2CA5E0?style=flat&logo=telegram&logoColor=white)](https://t.me/np_nbcn) |

---

## Credits / 致謝

This project is built upon the great work of the following open-source communities:  
*該項目建立在以下開源項目的出色工作之上:*

**Core:**
- [HSSkyBoy/Mihomo](https://github.com/HSSkyBoy/Mihomo/tree/Alpha)
- [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo)

**Android UI:**
- [HatsuneMikuUwU/MikuRay](https://github.com/HatsuneMikuUwU/MikuRay)
- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [2dust/v2rayNG](https://github.com/2dust/v2rayNG)

**Web Dashboard:**
- [MetaCubeX/Yacd-meta](https://github.com/MetaCubeX/Yacd-meta)
