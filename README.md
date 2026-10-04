# ZeroLogin

新一代轻量级 Minecraft 登录插件。**单个 JAR 兼容 Minecraft 1.20 ~ 26.1.2**。

## 特性

- 单 JAR 覆盖 1.20（Java 17）到 26.1.2（Java 25），编译目标 Java 17
- 安全口令存储：PBKDF2-HMAC-SHA256 加盐散列（零外部依赖）
- 未认证保护：冻结移动、拦截命令/聊天/破坏/放置/伤害/背包/丢弃
- 登录保护：超时踢出、密码错误次数限制
- 基于 IP 的自动登录与会话恢复（`/ip` 命令管理）
- 轻量文件存储（每账号一文件，玩家改名安全）
- 中英文消息，可配置
- 管理命令与统计

## 命令

| 命令 | 说明 |
|------|------|
| `/register <密码> <密码>` | 注册（别名 `/reg`） |
| `/login <密码>` | 登录（别名 `/l`） |
| `/unregister <当前密码>` | 注销账号 |
| `/changepassword <旧> <新>` | 修改密码（别名 `/cpw`） |
| `/ip <add\|remove\|list\|clear> [ip]` | 管理自动登录 IP |
| `/zerologin <reload\|info\|stats>` | 管理命令（别名 `/zl`） |

## 权限

- `zerologin.admin` — 使用 `/zerologin` 管理命令（默认 OP）
- `zerologin.bypass` — 免登陆（默认 false）
- `zerologin.ip` — 使用 `/ip`（默认 true）

## 构建

```bash
mvn clean package
```

产物：`target/ZeroLogin-<version>.jar`

### 兼容性门禁

针对 API 上下限分别编译，验证单 JAR 仅使用两端点 API 交集：

```bash
mvn clean package              # 对齐 1.20.1 API
mvn -Papi-latest clean package # 对齐 26.1.2 API
```

## 兼容性说明

- `api-version: '1.20'`：Paper 采用“api-version ≤ 服务端版本即加载”规则，故 1.20 声明可被 1.20 与 26.x 加载。
- 字节码 major 61（Java 17）：低版本字节码可直接运行于 Java 25 JVM。
- 仅使用 1.20 与 26.x 共有的 Bukkit API 交集。
