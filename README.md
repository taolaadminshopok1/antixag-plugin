<div align="center">

# AntiXag

### 🛡️ Anti X-Ray Protection for Paper / Spigot Servers — International Edition 2026

**Made by Madoez Vn · in collaboration with BakiCode and Alexander Vn**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.2%20--%2021.x-green.svg)](https://papermc.io/)
[![ProtocolLib](https://img.shields.io/badge/ProtocolLib-5.3.0-blue.svg)](https://github.com/dmulloy2/ProtocolLib)

[English](#english) · [Tiếng Việt](#tiếng-việt)

---

</div>

## ✨ Features

| | Feature | Description |
|---|---|---|
| 🧬 | **Packet-level obfuscation** | The chunk data is rewritten *before* it reaches the client. The world itself is never touched, so the server logic, other plugins and legitimate players always see the real blocks. |
| 🧠 | **Smart vein detection** | Ores are grouped into veins with a flood fill over the six face neighbours, so a partially mined vein never leaks the position of the remaining ores. |
| 🪨 | **Dimension aware camouflage** | Stone in the overworld, deepslate below y = 0, netherrack and basalt in the nether, end stone in the end. |
| 🎲 | **Randomised camouflage** | Every ore can be disguised with a random block out of the matching palette, which makes the caves look natural. |
| ⚡ | **Three hide strategies** | `GRADUALLY`, `FULL_VEIN` and `ALL` control how aggressively the ores are concealed. |
| 🔌 | **Live block updates** | Block changes are intercepted as well, so a placed or broken ore never reveals itself. |
| 🌍 | **Per world configuration** | Every world can be enabled, disabled or tuned on its own. |
| 🌐 | **Bilingual** | Full English and Vietnamese message files. |
| ⚙️ | **Modern commands** | `/antixag reload`, `/antixag status`, `/antixag debug`, `/antixag worlds`. |
| 🧩 | **Version independent** | Uses reflection instead of hardcoded NMS calls, so it keeps working from 1.20.2 up to 1.21.x and beyond. |

---

## 📋 Requirements

| Requirement | Version |
|---|---|
| Java | 17 or newer |
| Server | Paper / Spigot / Purpur 1.20.2+ |
| ProtocolLib | 5.1.0+ (5.3.0 recommended) |

---

## 📦 Installation

1. Download the latest release from the [releases page](https://github.com/taolaadminshopok1/antixag-plugin/releases).
2. Drop `AntiXag-2026.1.0.jar` into the `plugins` folder of your server.
3. Install [ProtocolLib](https://github.com/dmulloy2/ProtocolLib/releases) into the `plugins` folder as well.
4. Start the server. The `config.yml` and the `messages` folder are generated automatically.

---

## ⚙️ Configuration

A short overview of `config.yml`. Every option is documented inside the file itself.

```yaml
settings:
  debug: false              # Prints detailed information into the console.
  language: en              # en or vi
  autosave-seconds: 60
  restore-on-unload: true
  hide-placed-blocks: true
  protect-from-explosions: true
  hide-only-in-survival: false
  excluded-worlds: []

defaults:
  enabled: true
  hidden-blocks:
    - COAL_ORE
    - DEEPSLATE_COAL_ORE
    - DIAMOND_ORE
    - DEEPSLATE_DIAMOND_ORE
    # ... and many more
  hide-mode: GRADUALLY
  reveal-mode: SINGLE
  camouflage:
    randomize: true
    overworld: [STONE, ANDESITE, DIORITE, GRANITE, TUFF]
    deepslate: [DEEPSLATE, DEEPSLATE_ANDESITE, TUFF]
    nether:    [NETHERRACK, BASALT, BLACKSTONE]
    end:       [END_STONE]

worlds:
  # Per world overrides, for example:
  # world_nether:
  #   enabled: false
```

### Hide modes

| Mode | Behaviour |
|---|---|
| `GRADUALLY` | Only ores that are **completely buried** are hidden. Ores that are already exposed inside a cave stay visible, which keeps the world looking natural. |
| `FULL_VEIN` | As soon as **one** block of a vein is buried, the **whole vein** is hidden. Prevents a half mined vein from leaking the position of the remaining ores. |
| `ALL` | **Every** configured ore is hidden, even exposed ones. The strictest mode. |

### Reveal modes

| Mode | Behaviour |
|---|---|
| `SINGLE` | Only the ore that became exposed is revealed. |
| `VEIN` | The whole vein is revealed as soon as one member becomes exposed. |

---

## 🎮 Commands

| Command | Permission | Description |
|---|---|---|
| `/antixag reload` | `antixag.admin` | Reloads the configuration and the language files. |
| `/antixag status` | `antixag.admin` | Shows the version, the server, the counters and the protected worlds. |
| `/antixag debug` | `antixag.admin` | Toggles the debug output in the console. |
| `/antixag worlds` | `antixag.admin` | Lists every world and shows whether it is protected. |
| `/antixag help` | `antixag.admin` | Shows the help page. |

Aliases: `/antixray`, `/axag`.

---

## 🔐 Permissions

| Permission | Default | Description |
|---|---|---|
| `antixag.admin` | op | Allows managing the plugin. |
| `antixag.bypass` | false | Allows bypassing the protection. |

---

## 🏗️ Building from source

```bash
git clone https://github.com/taolaadminshopok1/antixag-plugin.git
cd antixag-plugin
mvn clean package
```

The finished plugin is written to `target/AntiXag-2026.1.0.jar`.

Requirements for building: JDK 17 and Maven 3.8 or newer.

---

## 🏛️ Architecture

```
vn.madoez.antixag
├── AntiXagPlugin            The entry point of the plugin
├── command/                 The /antixag command
├── config/                  The configuration model
├── engine/                  The ore detection and vein scanning
├── lang/                    The bilingual message manager
├── listener/                The packet and block event listeners
├── protocol/                The wire format handling
│   ├── BlockStateRegistry   Maps Bukkit materials to protocol ids
│   ├── ByteCursor           Reads and writes the protocol primitives
│   ├── ChunkObfuscator      Rewrites the chunk data
│   ├── ChunkPacketCodec     Locates the section buffer of the packet
│   └── PalettedContainer    Decodes and encodes the paletted containers
└── util/                    Small helper classes
```

The obfuscator decodes the paletted container of every chunk section, groups
the ores into veins, decides which ones are buried and replaces them with
their camouflage state. The biome container is never touched, it is copied
verbatim so that the biome data can not be corrupted.

---

## 📄 License

AntiXag is licensed under the **GNU General Public License v3.0**.

```
Copyright (C) 2026 Madoez Vn, BakiCode, Alexander Vn

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

The full text of the license can be found in [LICENSE](LICENSE).

---

<div align="center">

### ✨ Features

| | Tính năng | Mô tả |
|---|---|---|
| 🧬 | **Ẩn quặng ở tầng gói mạng** | Dữ liệu chunk được sửa lại *trước khi* gửi tới client. Thế giới thật sự không bị thay đổi, nên logic máy chủ, các plugin khác và người chơi bình thường luôn thấy quặng thật. |
| 🧠 | **Phát hiện mạch quặng thông minh** | Quặng được nhóm thành mạch bằng thuật toán lan tràn trên 6 mặt, nên một mạch đào dở một nửa sẽ không làm lộ vị trí của các quặng còn lại. |
| 🪨 | **Ngụy trang theo chiều không gian** | Đá ở Overworld, deepslate dưới y = 0, netherrack và basalt ở Nether, end stone ở End. |
| 🎲 | **Ngụy trang ngẫu nhiên** | Mỗi viên quặng có thể được giấu bằng một khối ngẫu nhiên trong bảng màu tương ứng, giúp hang động trông tự nhiên. |
| ⚡ | **Ba chế độ ẩn** | `GRADUALLY`, `FULL_VEIN` và `ALL` điều khiển mức độ che giấu quặng. |
| 🔌 | **Cập nhật khối tức thì** | Gói tin thay đổi khối cũng bị chặn, nên quặng được đặt hoặc phá sẽ không bao giờ tự lộ. |
| 🌍 | **Cấu hình riêng từng thế giới** | Mỗi thế giới có thể bật, tắt hoặc tinh chỉnh riêng. |
| 🌐 | **Đa ngôn ngữ** | Bộ tin nhắn tiếng Anh và tiếng Việt đầy đủ. |
| ⚙️ | **Lệnh hiện đại** | `/antixag reload`, `/antixag status`, `/antixag debug`, `/antixag worlds`. |
| 🧩 | **Đa phiên bản** | Dùng reflection thay vì gọi NMS cứng, nên vẫn chạy được từ 1.20.2 tới 1.21.x và mới hơn. |

---

## 📋 Yêu cầu

| Yêu cầu | Phiên bản |
|---|---|
| Java | 17 trở lên |
| Máy chủ | Paper / Spigot / Purpur 1.20.2+ |
| ProtocolLib | 5.1.0+ (khuyến nghị 5.3.0) |

---

## 📦 Cài đặt

1. Tải bản mới nhất từ [trang Releases](https://github.com/taolaadminshopok1/antixag-plugin/releases).
2. Chép `AntiXag-2026.1.0.jar` vào thư mục `plugins` của máy chủ.
3. Cài thêm [ProtocolLib](https://github.com/dmulloy2/ProtocolLib/releases) vào thư mục `plugins`.
4. Khởi động máy chủ. File `config.yml` và thư mục `messages` sẽ được tạo tự động.

---

## 🎮 Lệnh

| Lệnh | Quyền | Mô tả |
|---|---|---|
| `/antixag reload` | `antixag.admin` | Tải lại cấu hình và ngôn ngữ. |
| `/antixag status` | `antixag.admin` | Xem phiên bản, máy chủ, bộ đếm và thế giới được bảo vệ. |
| `/antixag debug` | `antixag.admin` | Bật/tắt chế độ gỡ lỗi trong console. |
| `/antixag worlds` | `antixag.admin` | Liệt kê các thế giới và trạng thái bảo vệ. |
| `/antixag help` | `antixag.admin` | Hiển thị trang trợ giúp. |

Lệnh thay thế: `/antixray`, `/axag`.

---

## 🔐 Quyền

| Quyền | Mặc định | Mô tả |
|---|---|---|
| `antixag.admin` | op | Quản lý plugin. |
| `antixag.bypass` | false | Bỏ qua cơ chế bảo vệ. |

---

## 🏗️ Build từ mã nguồn

```bash
git clone https://github.com/taolaadminshopok1/antixag-plugin.git
cd antixag-plugin
mvn clean package
```

File plugin hoàn chỉnh nằm ở `target/AntiXag-2026.1.0.jar`.

---

## 📄 Giấy phép

AntiXag được phát hành theo giấy phép **GNU General Public License v3.0**.

```
Copyright (C) 2026 Madoez Vn, BakiCode, Alexander Vn

Chương trình này là phần mềm tự do: bạn có thể phân phối lại và/hoặc
sửa đổi nó theo các điều khoản của Giấy phép Công cộng GNU phiên bản 3
do Free Software Foundation ban hành.

CHƯƠNG TRÌNH NÀY ĐƯỢC CUNG CẤP "NGUYÊN TRẠI", KHÔNG BẢO ĐẢM BẤT KÌ
ĐIỀU KHOẢN NÀO, BAO GỒM CẢ ĐỘ CHÍNH XÁC THƯƠNG MẠI VÀ PHÙ HỢP ĐỂ
SỬ DỤNG MỤC ĐÍCH CỤ THỂ.
```

Toàn bộ văn bản giấy phép nằm ở [LICENSE](LICENSE).

</div>

---

<div align="center">

**AntiXag** — Made with 💎 by **Madoez Vn**, **BakiCode** and **Alexander Vn**

[![GitHub](https://img.shields.io/badge/github-taolaadminshopok1%2Fantixag--plugin-blue)](https://github.com/taolaadminshopok1/antixag-plugin)

</div>
