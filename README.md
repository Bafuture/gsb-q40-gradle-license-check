# Gradle License Check Plugin

一个从零实现的 Gradle 插件：在上线前自动采集项目**运行时**依赖（含传递依赖）的许可证，
按白名单 / 黑名单 / 坐标覆盖规则进行合规判定，生成人类可读的文本报告与机器可读的 JSON 报告，
并可作为 CI 门禁在违规时让构建失败。

- 插件 id：`com.example.gsb.license-check`
- 任务名：`licenseCheck`（自动挂到 Java 项目的 `check` 任务上）
- 运行环境：Gradle 8.x、JDK 17
- 零运行时第三方依赖（仅依赖 Gradle API 与 JDK 标准库）

## 快速开始

```groovy
plugins {
    id 'java'
    id 'com.example.gsb.license-check'
}

licenseCheck {
    // 白名单：允许出现的许可证（按 POM 中 <name> 或 <url> 匹配）
    allowedLicenses = [
        'Apache License, Version 2.0',
        'MIT License',
        'BSD-2-Clause',
        'BSD-3-Clause'
    ]

    // 黑名单：出现即禁止（可选）
    denyLicense 'GNU General Public License, Version 3'
    denyLicense 'GNU Affero General Public License v3.0'

    // 坐标覆盖：法务已逐个人工评审通过 / 封杀的构件
    allowCoordinate 'com.vendor:internal-utils:*', '工单 LEGAL-42，已人工确认'
    denyCoordinate  'com.vendor:evil-lib:*',     '许可证与商用冲突'

    // 仅告警，不让构建失败（默认 false，即门禁开启）
    lenient = false
}
```

运行：

```bash
./gradlew licenseCheck   # 或 ./gradlew check
```

输出：

- `build/reports/license-check/license-report.txt` —— 人类可读报告
- `build/reports/license-check/license-report.json` —— 机器可读报告

## DSL 说明

| 配置项 / 方法 | 类型 | 说明 |
|---|---|---|
| `allowedLicenses` | `ListProperty<String>` | 许可证白名单 |
| `deniedLicenses`（`denyLicense(String)` 追加） | `ListProperty<String>` | 许可证黑名单 |
| `allowCoordinates`（`allowCoordinate(pattern[, reason])`） | `MapProperty<String,String>` | 坐标级允许规则，value 为原因 |
| `denyCoordinates`（`denyCoordinate(pattern[, reason])`） | `MapProperty<String,String>` | 坐标级禁止规则，value 为原因 |
| `lenient` | `Property<Boolean>` | `true` 时仅告警不失败，默认 `false` |
| `reportsDir` | `DirectoryProperty` | 报告输出目录，默认 `build/reports/license-check` |
| `textReport` / `jsonReport` | `Property<Boolean>` | 是否输出对应报告，默认均为 `true` |

## 规则语义

对运行时 classpath 上的**每一个外部构件**（`group:name:version`，含传递依赖；
本工程自身产出与项目内部模块不计入），按以下顺序判定：

1. **坐标 deny 规则**命中 → `denied`（最高优先级，直接否决）。
2. **坐标 allow 规则**命中 → `allowed`（即使许可证未知也放行）。
3. 已声明的许可证中**任意一条**在黑名单 → `denied`（黑名单优先于白名单）。
4. 已声明的许可证**全部**在白名单 → `allowed`；只要有一条不在白名单 → `unknown`。
5. POM 中没有任何许可证元数据、或 POM 无法解析/获取 → `unknown`。

要点：

- 无法识别许可证的构件会显式标记为 **`unknown`** 并写入报告，绝不会被静默跳过。
- 多许可证构件采用「全白才放行、一黑即否决」的保守策略，更符合法务默认立场。
- 坐标匹配格式为 `group:name:version`，支持 `*` 通配（如
  `com.vendor:foo:*`、`com.vendor:*:*`）；其余匹配不区分大小写，许可证比较会
  trim 并折叠空白，可使用 POM 中的 license name 或 URL。

## 门禁行为

默认（`lenient = false`）下，存在任一 `denied` 或 `unknown` 构件时：

- 任务抛出 `GradleException` 并失败，`./gradlew` 返回**非零退出码**；
- 报告仍会完整写出，`status` 为 `fail`；违规明细同时以 `warn` 日志打印。

设置 `lenient = true` 后：

- 任务成功、退出码为 0；
- 报告 `status` 变为 `warn`，构建日志仍会逐条列出违规项。

## 报告示例（文本）

```
Component : org.fake:delta:1.5
Licenses  : GNU General Public License, Version 3
Source    : pom metadata: repo/org/fake/delta/1.5/delta-1.5.pom
Verdict   : denied
Matched   : license blacklist: 'GNU General Public License, Version 3'
------------------------------------------------------------
Component : org.fake:gamma:3.0
Licenses  : UNKNOWN
Source    : pom metadata (no <licenses> entry): .../gamma-3.0.pom
Verdict   : unknown
Matched   : no license metadata found
------------------------------------------------------------

Summary
-------
Total components : 4
Allowed          : 2
Denied           : 1
Unknown          : 1

Result: FAIL - 2 violation(s):
  * DENIED  org.fake:delta:1.5 - license blacklist: 'GNU General Public License, Version 3'
  * UNKNOWN org.fake:gamma:3.0 - no license metadata found
```

JSON 报告顶层包含 `status`（`pass` / `warn` / `fail`）、`lenient`、`summary`
（total / allowed / denied / unknown）、`components[]`（group、name、version、
coordinates、licenses[{name,url}]、verdict、matchedRule、licenseSource）以及
`violations[]`。报告内容是确定性的（不包含时间戳），避免污染增量缓存。

## 缓存与增量

`licenseCheck` 正确声明了输入与输出：

- `@InputFiles`：运行时 classpath 的构件文件（jar 等）；
- `@InputFiles`：每个构件对应的 POM 元数据文件（相对路径敏感、按内容追踪）；
- `@Input`：白名单、黑名单、坐标规则与 `lenient` 开关；
- `@OutputDirectory`：报告目录。

上述输入均未变化时，重复执行任务显示 `UP-TO-DATE`；jar 不变但 POM 内容变化
（例如补充了许可证）同样会触发重新判定。

## 许可证信息来源

插件通过 Gradle 的依赖解析引擎解析 `runtimeClasspath`（含传递依赖），再以
`group:name:version@pom` 的非传递 detached configuration 从 Gradle 使用的同一批
本地缓存 / 远程仓库中获取每个构件的 POM，并从 `<licenses>` 节点提取许可证 name/url。
因此：

- 仓库只需可被 Gradle 解析（本地 mavenLocal、公司内 Nexus、文件仓库均可）；
- 已下载过的构件走本地缓存，无需重新联网。

## 已知限制

1. **不解析 POM 父级继承**：只读取构件自身 POM 的 `<licenses>`，不沿
   `<parent>` 向上继承许可证。现实中绝大多数构件在自身 POM 或其直接父 POM 中
   声明许可证；仅在祖父级及以上声明的许可证会被记为 `unknown`。
2. **不读取 Gradle Module Metadata 的 license 信息**：Gradle Module Metadata
   目前未标准化许可证字段，许可证统一以 Maven POM 为准。
3. **不做 SPDX 归一化**：白/黑名单按名称/URL 字符串匹配（大小写不敏感、空白
   折叠），`Apache 2.0` 与 `Apache License, Version 2.0` 不会被视为同一条；
   建议直接采用 POM 中的标准名称，必要时把多个写法都加进白名单。
4. **不扫描 jar 内的 `META-INF/LICENSE`**，也不解析 jar 内打包的第三方代码
   （uber-jar / shaded jar 里被重定位的依赖不会单独列出）。
5. **只检查外部 Maven 构件**：文件依赖（`files(...)`）、平台/BOM 约束、
   项目内部子模块不在检查范围内。
6. **当前不兼容 Gradle configuration cache**：任务执行时使用了
   `Project` 级 API 解析 detached configuration；普通增量构建与 Build Cache
   不受影响。
7. 固定检查 `runtimeClasspath`（对应 Java/Java Library/Kotlin JVM 项目的
   `implementation`/`runtimeOnly` 依赖）；应用了非 JVM 插件的工程不会注册任务。

## 测试

`src/test/java` 下使用 **Gradle TestKit + JUnit 5** 编写集成测试，测试工程使用
临时目录内构造的假 Maven 仓库（POM + 空 jar，包含传递依赖），全程 `--offline`，
不依赖任何网络下载：

- `passesWhenAllLicensesWhitelisted` —— 合规通过，验证文本与 JSON 报告；
- `failsWhenBlacklistedLicenseIsDeclared` —— 黑名单命中导致任务失败（非零退出）；
- `failsByDefaultForUnknownLicense` / `warnsAndSucceedsForUnknownLicenseWhenLenient`
  —— 未知许可证默认失败、`lenient` 下仅告警且成功；
- `coordinateAllowRuleOverridesUnknownLicense` /
  `coordinateDenyRuleOverridesWhitelistedLicense` —— 坐标覆盖规则的两个方向；
- `isUpToDateWhenInputsDoNotChange` —— 输入未变时第二次执行 `UP-TO-DATE`。

```bash
./gradlew test
```
