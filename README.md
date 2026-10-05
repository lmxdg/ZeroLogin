# ZeroLogin

> 新一代轻量级 Minecraft 服务器登录插件 —— **单个 JAR 兼容 Minecraft 1.20 ~ 26.1.2**

ZeroLogin 为离线模式（`online-mode=false`）服务器提供账号注册与登录保护：玩家首次进入需注册，之后每次进入需登录；在完成认证前，插件会冻结其移动并拦截各类交互，防止未认证玩家破坏服务器。

[![Release](https://img.shields.io/badge/release-v1.1.0-blue)](https://github.com/lmxdg/ZeroLogin/releases/tag/v1.1.0)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20%20~%2026.1.2-green)](#兼容性)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](#运行环境)
[![Storage](https://img.shields.io/badge/storage-SQLite%20%7C%20file-yellow)](#存储后端)
[![Languages](https://img.shields.io/badge/i18n-7%20languages-lightgrey)](#多语言)
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
- [存储后端](#存储后端)
- [多语言](#多语言)
- [账号数据](#账号数据)
- [数据迁移](#数据迁移)
- [安全说明](#安全说明)
- [从源码构建](#从源码构建)
- [常见问题](#常见问题)
- [开源协议](#开源协议)

---

## 功能特性

### 账号系统
- **注册 / 登录 / 注销 / 改密**：完整的账号生命周期管理
- **安全口令存储**：PBKDF2-HMAC-SHA256 + 随机盐，散列格式为 `$pbkdf2$<迭代次数>$<盐>$<散列>`
- **安全口令散列零依赖**：加密部分仅使用 JDK 自带实现，不会因第三方库版本冲突或类加载隔离而失败。唯一打包内联的是 SQLite 驱动，且已 relocate 到独立包名
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
- **SQLite 存储后端**：默认单文件数据库（WAL 模式），账号量级增大时仍保持稳定的读写性能
- **双后端与迁移**：`file` / `sqlite` 两种后端可互换，内置非破坏式迁移命令，v1.0 老数据自动导入
- **多语言**：内置 `zh_cn` / `zh_tw` / `en_us` / `ja_jp` / `ko_kr` / `ru_ru` / `es_es` 七种语言，`language: auto` 时跟随玩家客户端语言
- **权限体系**：细粒度权限节点，兼容主流权限插件
- **管理命令**：`/zerologin reload|info|stats|migrate|lang-template`

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

1. 前往 [Releases](https://github.com/lmxdg/ZeroLogin/releases) 下载最新版 `ZeroLogin.jar`
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
| `/zerologin <reload\|info\|stats\|migrate\|lang-template>` | `/zl` | 管理命令 | — |

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
| `/zerologin info` | 查看插件版本、兼容区间、存储后端与当前语言 |
| `/zerologin stats` | 查看注册账号数与当前在线已认证人数 |
| `/zerologin migrate <from> <to> [force]` | 在 `file` 与 `sqlite` 之间迁移账号数据；目标非空时需加 `force` |
| `/zerologin lang-template <lang>` | 导出指定语言的内置消息模板，便于翻译自定义 |

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
# 界面语言：auto（跟随每位玩家的客户端语言）
# 也可强制指定：en_us / zh_cn / zh_tw / ja_jp / ko_kr / ru_ru / es_es
language: auto

storage:
  # 账号数据后端：sqlite（推荐）或 file（v1.0 的本地文本文件）
  type: sqlite

  sqlite:
    # 数据库文件名，位于 plugins/ZeroLogin/ 下
    file: zerologin.db
    # 数据库为空时是否自动导入 file 后端的账号数据
    import-file: true

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
| `language` | 字符串 | `auto` | `auto` 跟随玩家客户端语言；或固定为 7 种语言之一。`auto` 时控制台与无法识别的客户端语言回退 `en_us` |
| `storage.type` | 字符串 | `sqlite` | 存储后端：`sqlite` 或 `file`；无法识别的值回退 `file` |
| `storage.sqlite.file` | 字符串 | `zerologin.db` | SQLite 数据库文件名（位于插件数据目录） |
| `storage.sqlite.import-file` | 布尔 | `true` | 数据库为空时自动导入 `accounts/` 下的文本账号 |
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

> 从 v1.0 升级时，旧 `config.yml` 中缺失的键会按上述默认值处理，无需手动补全即可运行；但建议比对一次以获得新配置项的注释。

---

## 存储后端

v1.1.0 起提供两种账号存储后端，通过 `storage.type` 切换。

### SQLite（默认，推荐）

- **单文件数据库**：`plugins/ZeroLogin/zerologin.db`，备份只需复制一个文件
- **WAL 模式**：读写并发更好，注册/登录高峰不阻塞读取
- **不阻塞主线程**：所有 SQL 在专用 IO 线程上串行执行，调用方拿到 `CompletableFuture`
- **驱动内联**：`sqlite-jdbc` 在打包时内联并 relocate 到 `dev.zerologin.libs.sqlite`，不依赖服务端或其他插件提供的驱动版本
- **改名安全**：按 `name_lc`（小写名列）建唯一索引，玩家改名后按新名字仍可命中

表结构：

```sql
CREATE TABLE accounts (
  uuid           TEXT    NOT NULL PRIMARY KEY,
  name           TEXT    NOT NULL,
  name_lc        TEXT    NOT NULL,           -- 小写玩家名，用于大小写不敏感查询
  password       TEXT    NOT NULL,           -- $pbkdf2$<迭代>$<盐>$<散列>
  registered_at  INTEGER NOT NULL DEFAULT 0,
  last_login_at  INTEGER NOT NULL DEFAULT 0,
  last_seen_at   INTEGER NOT NULL DEFAULT 0,
  login_count    INTEGER NOT NULL DEFAULT 0,
  auto_login_ips TEXT    NOT NULL DEFAULT '' -- 逗号分隔
);
CREATE UNIQUE INDEX idx_accounts_name_lc ON accounts(name_lc);
```

### file（v1.0 后端，保留兼容）

每账号一个纯文本文件，人工可读、可直接同步到其他服务器。适用于账号数很少、或希望用共享目录做多服共享的场景。

### 如何选择

| 场景 | 建议 |
|------|------|
| 一般服务器 / 账号数较多 | `sqlite` |
| 需要把账号目录软链到共享存储 | `file` |
| 从 v1.0 升级 | `sqlite`（首次启动自动导入旧数据） |

---

## 多语言

内置 7 种语言，`language: auto` 时**每位玩家按其客户端语言**收到提示，互不影响：

| 语言标签 | 语言 |
|----------|------|
| `zh_cn` | 简体中文 |
| `zh_tw` | 繁體中文 |
| `en_us` | English |
| `ja_jp` | 日本語 |
| `ko_kr` | 한국어 |
| `ru_ru` | Русский |
| `es_es` | Español |

- `auto` 模式下，控制台与无法识别的客户端语言回退 `en_us`
- 玩家语言按 `Player#getLocale()` 读取并做前缀归一化：`zh_SG` → `zh_cn`、`zh_TW`/`zh_HK`/`zh_hant` → `zh_tw`、`en_GB` → `en_us`
- 想新增语言：执行 `/zerologin lang-template <lang>` 导出模板，翻译后放回 `plugins/ZeroLogin/`，再 `/zerologin reload`

---

## 账号数据

### SQLite 后端（默认）

路径：`plugins/ZeroLogin/zerologin.db`

账号存放在 `accounts` 表中（表结构见 [存储后端](#存储后端)），口令散列仍以 `$pbkdf2$<迭代>$<盐>$<散列>` 形式保存在 `password` 列。

- **改名安全**：主键是 `uuid`，改名只更新 `name` / `name_lc`，不会丢数据
- **单行更新**：写账号使用 `INSERT ... ON CONFLICT(uuid) DO UPDATE`，只改该玩家一行
- **批量迁移用单事务**：迁移与导入走 `saveAll`，一次提交，失败整体回滚
- **完整性自检**：启动时执行 `PRAGMA integrity_check`，异常只告警不阻断启用
- **备份**：停止服务端后复制 `zerologin.db`（若存在 `-wal` / `-shm` 侧文件，一并复制或直接跑一次 checkpoint）

### file 后端

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

- **文件名为 UUID**：玩家改名不会造成主键冲突或数据丢失；按名字查询时插件会命中索引并自动更新记录
- **单账号单文件写入**：登录高峰只重写对应玩家的小文件，不会整库重写
- **原子写入**：先写 `.tmp` 再原子替换，避免服务端崩溃导致文件损坏
- **可迁移**：整个 `accounts/` 目录可直接复制到其他服务器复用

> ⚠️ 手工编辑账号文件有风险，可能导致该账号无法读取（会被安全跳过并记录警告）。

---

## 数据迁移

### 从 v1.0 升级到 v1.1.0

1. 停止服务端，备份 `plugins/ZeroLogin/`
2. 用新版 JAR 替换旧 JAR
3. 确认 `config.yml` 中 `storage.type: sqlite`（若旧配置写的是 `file`，改成 `sqlite`）
4. 启动服务端

首次以 SQLite 启动且数据库为空时，插件会自动把 `accounts/` 下的账号导入数据库，并输出：

```text
[ZeroLogin] 已把 N 个账号从文本文件导入 SQLite，原 accounts/ 目录未修改。
```

自动导入是**非破坏式**的：`accounts/` 目录原样保留，失败只告警、不影响插件启用，之后仍可用下面的命令手动重试。若想关闭自动导入，设置 `storage.sqlite.import-file: false`。

### 手动迁移命令

```text
/zerologin migrate <from> <to> [force]
```

| 情形 | 行为 |
|------|------|
| 目标后端为空 | 直接导入 |
| 目标后端已有账号 | **中止**并提示数量，需显式加 `force` |
| 加了 `force` | 先把目标后端现有账号导出为文本备份到 `backups/migrate-<时间戳>/`，再导入 |
| 同名但 UUID 不同 | 跳过该账号并汇报跳过数量（保留任一方都会丢数据） |
| 源后端为空 | 提示无可迁移数据 |

示例：

```text
/zerologin migrate file sqlite
/zerologin migrate sqlite file
/zerologin migrate file sqlite force
```

> 迁移在异步线程执行，不阻塞服务端主线程；且只通过存储抽象接口读写，未来新增后端无需改迁移代码。

---

## 安全说明

- **口令散列**：采用 PBKDF2-HMAC-SHA256 加盐存储，即使账号数据泄露也无法直接还原明文口令。默认 120000 次迭代。
- **口令及时擦除**：口令在校验完成后立即用零填充清除内存中的字符数组，减少驻留时间。
- **不阻塞主线程**：账号读写全部在存储后端的专用 IO 线程执行，主线程只处理 Future 回调，避免登录高峰卡顿。
- **在线模式建议**：若服务端可开启正版验证（`online-mode=true`），通常无需本插件；本插件面向离线/盗版服务器场景。
- **自动登录权衡**：IP 自动登录提升了体验，但同一 IP 下的其他玩家可能借道进入。对安全要求高的服务器，建议将 `session.ip-auto-login` 设为 `false`，并引导玩家不添加 IP 白名单。
- **权限授予**：`zerologin.bypass` 会让玩家跳过全部认证，请仅授予可信人员。
- **账号文件保护**：`zerologin.db` 与 `accounts/*.txt` 含口令散列，请限制文件权限，勿公开分享备份。
- **迁移可回滚**：`force` 迁移前会自动导出目标后端现有账号为文本备份，误操作可回滚。

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

产物：`target/ZeroLogin-<版本>.jar`（约 14 MB，已内联并 relocate SQLite 驱动）

### 运行测试

```bash
mvn test
```

共 43 个单元测试，覆盖口令散列、密码校验、账号编解码、文件与 SQLite 存储读写、以及后端间数据迁移。

### 兼容性门禁

针对 API 上下限分别编译，确保源码只使用两端点 API 的交集：

```bash
mvn clean package              # 对齐 1.20.1 API（下限）
mvn -Papi-latest clean package # 对齐 26.1.2 API（上限）
```

两条命令均编译通过，才说明单 JAR 在 1.20 ~ 26.1.2 全区间可用。另有 `api-1202`、`api-12111` 两个中间版本 Profile 用于额外抽查。

---

## 常见问题

**Q：支持 Folia 吗？**
A：暂不支持。`plugin.yml` 中已声明 `folia-supported: false`。

**Q：玩家忘了密码怎么办？**
A：默认（SQLite）后端下执行 `DELETE FROM accounts WHERE name_lc = '<玩家名小写>'`（停止服务端后操作，或先 `/zerologin stats` 确认 UUID）。若使用 `file` 后端，删除 `plugins/ZeroLogin/accounts/<该玩家UUID>.txt` 即可。两种方式都会丢失该账号的 IP 白名单等数据，玩家下次进入需重新注册。

**Q：怎么切换到 SQLite / 换回文件存储？**
A：改 `storage.type` 后执行 `/zerologin reload` 不会切换存储（存储后端需重启才生效）。正确做法：在运行中的服务端执行 `/zerologin migrate <当前的后端> <目标后端>` 迁移数据 → 停止服务端 → 修改 `storage.type` → 启动服务端。建议在低峰期操作，避免迁移后又写入旧后端。

**Q：为什么改了 `storage.type` 重启后账号是空的？**
A：说明新后端里还没有数据。执行 `/zerologin migrate <旧后端> <新后端>` 导入即可；若目标后端已有数据，命令会中止以防覆盖，确认无误后加 `force`。

**Q：如何从其他登录插件迁移？**
A：内置迁移只支持本插件的 `file` ↔ `sqlite` 两种后端。从其他插件迁移可先按 `accounts/*.txt` 的纯文本格式（见 [账号数据](#账号数据)）写出文件，把目录放到 `plugins/ZeroLogin/accounts/`，再以 `sqlite` 启动触发自动导入，或用 `/zerologin migrate file sqlite`。

**Q：能跨多个服务器共享账号吗？**
A：SQLite 是单文件数据库，适合「整库复制」而不是多服并发读写同一文件。多服实时共享建议使用共享数据库方案（后续版本计划提供 MySQL 后端）；若必须共享目录，请把 `storage.type` 设为 `file` 并注意并发写入风险。

**Q：JAR 为什么有 14 MB？**
A：v1.1.0 把 SQLite JDBC 驱动（含各平台 native 库）内联进 JAR，并 relocate 到独立包名，避免与服务端其他插件的 sqlite-jdbc 版本冲突。插件自身的类仍很小。

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
