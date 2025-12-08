<div align="center">
<a name="readme-top"></a>
<h1><a href="https://www.uuvpn.com/" target="_blank">UUVPN🪜</a></h1>

## 一款适用于 Android 的 VPN 应用，支持所有 [V2Board](https://github.com/cedar2025/Xboard) 服务器

[![][license-shield]][license-link] [![][docs-shield]][docs-link] [![][github-release-shield]][github-release-link] [![][github-stars-shield]][github-stars-link]

English | [简体中文](README-zh_CN.md)
</div>
<br/>

## 什么是 UUVPN？
UUVPN 在 Android 上原生运行，采用强大的 CLASH(mihomo) 核心。

![](screenshots/android/combined_image11-19_12-04-1.jpeg)

### 新版本功能：

- 更流畅、响应更灵敏的用户界面
- 增强了应用稳定性
- 显著的性能优化
- 此外，UUVPN 现已支持服务器后端协议，并完全兼容 V2Board，为 VPN 管理提供更无缝、更灵活的用户体验。

### 主要功能：
- 原生性能：UUVPN 利用适用于 Android 的 CLASH，优化了稳定性和速度。
- 兼容 V2Board：完全支持 V2Board 协议，为后端管理提供流畅的集成。
- 更精致的用户界面：全新设计的界面，外观时尚，提供用户友好的体验。

### 服务器：
- https://github.com/cedar2025/Xboard
此接口地址用于处理 UUVPN 的核心功能，包括用户身份验证、配置获取和连接管理。

#### 协议兼容性
UUVPN 的 Android 版本与基于 https://github.com/cedar2025/Xboard 系列的服务器高度兼容，并支持以下常用协议：

- Shadowsocks：提供安全的 SOCKS5 代理，支持加密传输。

- VMess：支持多种传输方式（如 TCP、WebSocket、QUIC 等），适用于高性能场景。

- VLESS：轻量级协议，兼容性强，支持 TCP、WebSocket 和 gRPC 等传输方式。

- Trojan：模拟 HTTPS 流量，支持 TLS 加密，隐蔽性强。

- SOCKS5：标准代理协议，支持 TCP 和 UDP 流量。

HTTP/HTTPS：支持 HTTP 代理和 HTTPS 加密连接。

* UUVPN 的 Android 版本与 Xboard 系列的服务器实现一致，理论上支持所有 Xboard 兼容的协议配置。
具体协议支持情况可能取决于服务器端的配置，请确保服务器端正确启用相关协议。
对于特殊协议（例如 TUIC 或 Hysteria），您需要确认服务器端是否已部署支持。
- 兼容性说明
跨平台一致性：Android 版本使用相同的服务器接口地址，以确保用户在不同设备上获得一致的体验。
版本兼容性：UUVPN 的客户端向下兼容基于 Xboard 的服务器版本，适用于大多数 Xboard 系列部署。
扩展性：如果您需要支持其他协议（例如 WireGuard 或 Hysteria2），请参考 Xboard 文档进行服务器端配置调整。 *

### 下载 Android 版本
- [Android Apk](https://github.com/nicolastinkl/UUVPN/releases)

# Android 应用程序项目

## [Android YouTube 视频预览地址](https://youtube.com/shorts/zI1hrpFJbtg?feature=share)

![](屏幕nshots/combined_image12-31_10-39.jpeg) 

## License

UUVPN is licensed under the **AGPLv3 License**. This means you can:

✅ Use the software for free.  
✅ Modify and distribute the code.  
✅ Use it privately without restrictions.

See the [LICENSE](LICENSE) file for more details.

---

<!-- UUVPN Other link-->
[license-link]: https://www.gnu.org/licenses/agpl-3.0.html 

<!-- Shield link-->
[license-shield]: https://img.shields.io/badge/License-MIT-blue.svg
[license-link]: https://github.com/nicolastinkl/UUVPN/blob/main/LICENSE
[docs-shield]: https://img.shields.io/badge/Docs-Latest-green.svg
[docs-link]: https://github.com/cedar2025/Xboard/blob/master/docs
[github-release-shield]: https://img.shields.io/github/v/release/nicolastinkl/UUVPN
[github-release-link]: https://github.com/nicolastinkl/UUVPN/releases
[github-stars-shield]: https://img.shields.io/github/stars/nicolastinkl/UUVPN
[github-stars-link]: https://github.com/nicolastinkl/UUVPN/stargazers
