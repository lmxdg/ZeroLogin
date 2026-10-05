# ZeroLogin

> 新一代轻量级 Minecraft 服务器登录插件 —— **单个 JAR 兼容 Minecraft 1.20 ~ 26.1.2**

ZeroLogin 为离线模式（`online-mode=false`）服务器提供账号注册与登录保护：玩家首次进入需注册，之后每次进入需登录；在完成认证前，插件会冻结其移动并拦截各类交互，防止未认证玩家破坏服务器。

[![Release](https://img.shields.io/badge/release-v1.0.0-blue)](https://github.com/lmxdg/ZeroLogin/releases/tag/v1.0.0)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20%20~%2026.1.2-green)](#兼容性)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](#运行环境)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](#开源协议)

---

## 目录

- [功能特性](#功能特性)
- [兼容性](#兼容性)
- [运行环境](#运行环境)
- [安装](#安装)
- [快速上手](#快速上手)
- [命令](#命令)
- [权限](#权限)
- [配置文件](#配置文件)
- [账号数据](#账号数据)
- [安全说明](#安全说明)
- [从源码构建](#从源码构建)
- [常见问题](#常见问题)
- [开源协议](#开源协议)

---

## 功能特性

### 账号系统
- **注册 / 登录 / 注销 / 改密**：完整的账号生命周期管理
- **安全口令存储**：PBKDF2-HMAC-SHA256 + 随机盐，散列格式为 `$pbkdf2$<迭代次数>$<盐>$<散列>`
- **零外部依赖**：仅使用 JDK 自带加密实现，不会因依赖冲突或类加载隔离而失败
- **常量时间比较**：校验口令时使用 `MessageDigest.isEqual`，避免通过响应时间侧信道推断散列
- **透明参数升级**：玩家登录成功时，若存储的散列迭代次数低于当前配置，会自动升级为更强参数

### 登录保护（未认证状态拦截）
| 拦截项 | 说明 |
|--------|------|
| 移动 | 冻结位置位移，仅允许转动视角（可配置开关） |
| 命令 | 仅放行登录相关命令，其余命令一律拦截 |
| 聊天 | 禁止发言 |
| 方块 | 禁止破坏、放置 |
| 交互 | 禁止右键交互、丢弃物品 |
| 战斗 | 禁止受到伤害 |
| 背包 | 禁止打开背包、点击背包物品 |

### 会话与自动登录
- **IP 自动登录**：玩家可用 `/ip` 将当前 IP 加入白名单，之后从该 IP 进入免登录（最多 5 个 IP）
- **会话恢复**：同一 IP 在 `remember-seconds` 时间内重进无需再次登录
- **免登陆权限**：`zerologin.bypass` 权限节点可让指定玩家（如管理员）跳过认证

### 登录防护
- **超时踢出**：未在指定时间内完成登录/注册将被踢出
- **错误次数限制**：密码错误达到上限次数后自动踢出

### 其他
- **中英文双语**：内置 `zh_cn` / `en_us` 消息，可自由编辑
- **权限体系**：细粒度权限节点，兼容主流权限插件
- **管理命令**：`/zerologin reload|info|stats`
- **轻量存储**：每账号一个文本文件，玩家改名不丢数据，可直接备份或同步到其他服

---

## 兼容性

| 项目 | 说明 |
|------|------|
| 支持版本 | Minecraft **1.20 ~ 26.1.2**（Paper / Spigot 及其衍生服务端） |
| 单 JAR | 一个 JAR 覆盖全版本区间，无需按版本下载不同构建 |
| 字节码 | 编译目标 **Java 17**（class major 61），可直接运行于 Java 17 ~ 25 的 JVM |
| API 使用 | 仅使用 1.20 与 26.x 两端点共有的 Bukkit API 交集 |

**为什么单 JAR 就能兼容？**

1. **`api-version: '1.20'`** —— Paper 采用「`api-version` ≤ 服务端版本即允许加载」的规则，因此声明 1.20 即可同时被 1.20 与 26.x 服务端加载。
2. **低版本字节码向上兼容** —— Minecraft 1.20 运行于 Java 17，26.x 运行于 Java 25；Java 17 字节码可直接在 Java 25 JVM 上运行，无需多版本构建。
3. **只用 API 交集** —— 开发期对 1.20.1 与 26.1.2 两个 API 包做了类与方法签名逐一比对，确保调用的 API 在两端均存在。

> 已验证：在 Paper 26.1.2（Java 25）实机加载并启用成功；编译期对 1.20.1 API 亦通过。

---

## 运行环境

- 服务端：Paper / Spigot 1.20 ~ 26.1.2
- Java：**17 或更高**（跟随服务端要求的 Java 版本即可）
- 服务端模式：`online-mode=false`（离线模式 / 盗版服）

---

## 安装

### 方式一：下载成品（推荐）

1. 前往 [Releases](https://github.com/lmxdg/ZeroLogin/releases) 下载最新版 `ZeroLogin1.0.jar`
2. 将 JAR 放入服务端的 `plugins/` 目录
3. 重启服务端
4. 首次启动后，配置与语言文件会生成在 `plugins/ZeroLogin/`

### 方式二：从源码构建

见 [从源码构建](#从源码构建)。

---

## 快速上手

服务端重启后，玩家进入服务器会看到提示：

```
[ZeroLogin] 请先注册：/register <密码> <密码>
```

玩家执行注册：

```
/register mypassword mypassword
```

注册成功后自动完成登录。下次进入服务器时，提示变为：

```
[ZeroLogin] 请登录：/login <密码>
```

玩家执行：

```
/login mypassword
```

在登录前，玩家无法移动、聊天、破坏方块或打开背包。

### 给管理员：让某个玩家免登录

为玩家添加 `zerologin.bypass` 权限（默认仅 OP 的 `zerologin.*` 包含该节点，需显式授予）：

```
/lp user <玩家名> permission set zerologin.bypass true
```

---

## 命令

| 命令 | 别名 | 说明 | 需登录 |
|------|------|------|:------:|
| `/register <密码> <密码>` | `/reg` | 注册账号，成功后自动登录 | 否 |
| `/login <密码>` | `/l` | 登录账号 | 否 |
| `/unregister <当前密码>` | `/unreg` | 注销（删除）账号 | 是 |
| `/changepassword <旧密码> <新密码>` | `/changepw` `/cpw` | 修改密码 | 是 |
| `/ip <add\|remove\|list\|clear> [ip]` | — | 管理自动登录 IP | 是 |
| `/zerologin <reload\|info\|stats>` | `/zl` | 管理命令 | — |

### `/ip` 子命令

| 子命令 | 说明 |
|--------|------|
| `/ip add <ip>` | 添加自动登录 IP，上限 5 个 |
| `/ip remove <ip>` | 移除自动登录 IP |
| `/ip list` | 查看已保存的自动登录 IP |
| `/ip clear` | 清空全部自动登录 IP |

### `/zerologin` 子命令

| 子命令 | 说明 |
|--------|------|
| `/zerologin reload` | 重载配置与语言文件 |
| `/zerologin info` | 查看插件版本、兼容区间、存储后端 |
| `/zerologin stats` | 查看注册账号数与当前在线已认证人数 |

---

## 权限

| 权限节点 | 默认 | 说明 |
|----------|:----:|------|
| `zerologin.admin` | OP | 使用 `/zerologin` 管理命令 |
| `zerologin.bypass` | 否 | 免登录，进入服务器后无需认证 |
| `zerologin.ip` | 是 | 使用 `/ip` 管理自己的自动登录 IP |
| `zerologin.*` | OP | 包含以上全部权限 |

---

## 配置文件

路径：`plugins/ZeroLogin/config.yml`

```yaml
# 界面语言：zh_cn / en_us
language: zh_cn

storage:
  # 账号数据后端（当前版本支持 file）
  type: file

security:
  # PBKDF2-HMAC-SHA256 迭代次数，越大越安全、登录/注册越慢
  pbkdf2-iterations: 120000
  # 密码最短 / 最长长度
  min-password-length: 6
  max-password-length: 32
  # 密码最长尝试次数，超过后踢出；0 = 不限制
  max-login-attempts: 3
  # 未认证时必须在多少秒内完成登录/注册，否则踢出；0 = 不限制
  login-timeout-seconds: 60
  # 是否要求密码必须同时包含字母和数字
  require-mixed: false

session:
  # 记住登录状态的秒数：同一 IP 在该时间内重进无需再登录；0 = 关闭
  remember-seconds: 300
  # 是否允许基于 IP 的自动登录（配合 /ip 命令）
  ip-auto-login: true

protection:
  # 未认证时是否冻结移动
  freeze-movement: true
  # 未认证时允许执行的命令（不含斜杠，小写）
  allowed-commands:
    - login
    - l
    - register
    - reg

messages-prefix: "&8[&bZeroLogin&8] "
```

### 配置项说明

| 配置项 | 类型 | 默认 | 说明 |
|--------|------|------|------|
| `language` | 字符串 | `zh_cn` | 语言文件，对应 `messages_<语言>.yml` |
| `storage.type` | 字符串 | `file` | 存储后端（当前仅 `file`） |
| `security.pbkdf2-iterations` | 整数 | `120000` | 散列迭代次数，下限 1000 |
| `security.min-password-length` | 整数 | `6` | 密码最短长度 |
| `security.max-password-length` | 整数 | `32` | 密码最长长度 |
| `security.max-login-attempts` | 整数 | `3` | 密码错误上限，`0` 为不限制 |
| `security.login-timeout-seconds` | 整数 | `60` | 登录超时秒数，`0` 为不限制 |
| `security.require-mixed` | 布尔 | `false` | 是否强制密码含字母与数字 |
| `session.remember-seconds` | 整数 | `300` | 同 IP 会话保持时间，`0` 为关闭 |
| `session.ip-auto-login` | 布尔 | `true` | 是否启用 IP 自动登录 |
| `protection.freeze-movement` | 布尔 | `true` | 未认证时是否冻结移动 |
| `protection.allowed-commands` | 列表 | 登录相关命令 | 未认证时允许执行的命令 |
| `messages-prefix` | 字符串 | `&8[&bZeroLogin&8] ` | 消息前缀，支持 `&` 颜色代码 |

### 语言文件

- `plugins/ZeroLogin/messages_zh_cn.yml` —— 中文
- `plugins/ZeroLogin/messages_en_us.yml` —— 英文

消息支持 `&` 颜色代码与 `{占位符}`（如登录错误提示中的 `{left}` 表示剩余次数）。修改后执行 `/zerologin reload` 或重启生效。

---

## 账号数据

路径：`plugins/ZeroLogin/accounts/<UUID>.txt`

每个账号一个文件，格式为可读的 `key value` 文本：

```text
# ZeroLogin account record. Edit at your own risk.
schema 1
uuid 069a79f4-44e9-4726-a5be-fca90e38aaf5
name Steve
password $pbkdf2$120000$<盐>$<散列>
registered-at 1696000000000
last-login-at 1696000100000
last-seen-at 1696000100000
login-count 3
auto-login-ips 1.2.3.4,fe80::1
```

**设计要点**

- **文件名为 UUID**：玩家改名（含正版改名、`name` 字段变更）不会造成主键冲突或数据丢失；按名字查询时插件会命中索引并自动更新记录
- **单账号单文件写入**：登录高峰只重写对应玩家的小文件，不会整库重写
- **原子写入**：先写 `.tmp` 再原子替换，避免服务端崩溃导致文件损坏
- **可迁移**：整个 `accounts/` 目录可直接复制到其他服务器复用

> ⚠️ 手工编辑账号文件有风险，可能导致该账号无法读取（会被安全跳过并记录警告）。

---

## 安全说明

- **口令散列**：采用 PBKDF2-HMAC-SHA256 加盐存储，即使账号文件泄露也无法直接还原明文口令。默认 120000 次迭代。
- **口令及时擦除**：口令在校验完成后立即用零填充清除内存中的字符数组，减少驻留时间。
- **在线模式建议**：若服务端可开启正版验证（`online-mode=true`），通常无需本插件；本插件面向离线/盗版服务器场景。
- **自动登录权衡**：IP 自动登录提升了体验，但同一 IP 下的其他玩家可能借道进入。对安全要求高的服务器，建议将 `session.ip-auto-login` 设为 `false`，并引导玩家不添加 IP 白名单。
- **权限授予**：`zerologin.bypass` 会让玩家跳过全部认证，请仅授予可信人员。

---

## 从源码构建

### 环境要求

- JDK 17 或更高
- Maven 3.8+
- 可访问 PaperMC 仓库与 Maven 中央仓库

### 构建

```bash
git clone https://github.com/lmxdg/ZeroLogin.git
cd ZeroLogin
mvn clean package
```

产物：`target/ZeroLogin-<版本>.jar`

### 运行测试

```bash
mvn test
```

共 21 个单元测试，覆盖口令散列、密码校验、账号编解码与文件存储读写。

### 兼容性门禁

针对 API 上下限分别编译，确保源码只使用两端点 API 的交集：

```bash
mvn clean package              # 对齐 1.20.1 API（下限）
mvn -Papi-latest clean package # 对齐 26.1.2 API（上限）
```

两条命令均编译通过，才说明单 JAR 在 1.20 ~ 26.1.2 全区间可用。

---

## 常见问题

**Q：支持 Folia 吗？**
A：暂不支持。`plugin.yml` 中已声明 `folia-supported: false`。

**Q：玩家忘了密码怎么办？**
A：删除 `plugins/ZeroLogin/accounts/<该玩家UUID>.txt` 后让其重新注册即可（会丢失该账号的 IP 白名单等数据）。

**Q：如何从其他登录插件迁移？**
A：本插件使用独立的账号文件格式，暂不提供自动迁移工具。可基于 `accounts/*.txt` 的纯文本格式编写转换脚本。

**Q：能跨多个服务器共享账号吗？**
A：可以。将 `plugins/ZeroLogin/accounts/` 目录放到共享存储（或各服定期同步）。后续版本计划提供 MySQL 存储后端以支持更便捷的多服共享。

**Q：为什么未认证时玩家能动视角却不能移动？**
A：这是刻意的设计——避免玩家因「完全冻结」产生卡死感，同时阻止其位移影响其他玩家。

---

## 开源协议

本项目基于 [MIT License](LICENSE) 开源。

---

## 相关链接

- 仓库：https://github.com/lmxdg/ZeroLogin
- 发布下载：https://github.com/lmxdg/ZeroLogin/releases
- 问题反馈：https://github.com/lmxdg/ZeroLogin/issues
