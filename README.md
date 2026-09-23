# Gradle License Check Plugin

一个从零实现的 Gradle 插件（插件 id：`com.example.gsb.license-check`），用于在上线前
自动采集项目**运行时依赖（含传递依赖）**的第三方许可证，按白名单/黑名单/坐标覆盖
规则进行合规判定，输出文本与 JSON 报告，并以门禁方式拦截不合规构建。

要求 JDK 17+，Gradle 8.x。

## 快速开始

应用 `java`（或 `java-library` 等 JVM）插件后再应用本插件：

```groovy
plugins {
    id 'java'
    id 'com.example.gsb.license-check'
}

licenseCheck {
    // 白名单：许可证名称出现在此列表中的构件允许使用
    allowLicenses = [
        'Apache License, Version 2.0',
        'MIT License',
        'BSD-3-Clause'
    ]

    // 黑名单：命中即违规（优先级高于白名单）
    denyLicenses = [
        'GNU General Public License, version 3'
    ]

    // 未识别许可证（POM 中无 <licenses>）的构件默认视为违规；
    // 置 true 表示经法务确认后统一放行「未知」构件
    allowUnknown = false

    // true（默认）：存在违规时任务失败、构建非零退出
    // false：仅打印告警并在报告中标记 PASSED WITH WARNINGS，不阻断构建
    failOnError = true

    // 按构件坐标覆盖规则
    rules {
        // 精确 GAV：只覆盖指定版本
        register('com.legacy:gpl-thing:4.2') {
            allow 'GPL-3.0-with-classpath-exception'
        }
        // group:name：覆盖该构件的任意版本
        register('org.vendor:internal-blob') {
            // 该构件 POM 没有许可证元数据，法务单独确认放行
            allowUnknown true
        }
    }
}
```

运行：

```bash
./gradlew checkLicenses   # 也会随 check 任务一起执行
```

报告输出位置：

- 文本报告：`build/reports/license-check/license-report.txt`
- JSON 报告：`build/reports/license-check/license-report.json`

## 判定结果（Verdict）

| 判定 | 含义 |
|------|------|
| `ALLOWED` | 至少一个许可证命中白名单，或未知许可证被显式放行 |
| `DENIED` | 至少一个许可证命中黑名单 |
| `UNKNOWN` | POM 缺失/无法解析/未声明任何许可证，且没有生效的 `allowUnknown` 规则 —— **构件仍会被列出并参与门禁，绝不静默跳过** |
| `UNLISTED` | 许可证可识别但既不在白名单也不在黑名单（信息性结果，本身不构成违规） |

## 规则语义

1. **许可证名称匹配**：按 POM `<licenses>/<license>/<name>` 的文本做**精确、区分大小写**
   的匹配（仅去除首尾空白）。不做 SPDX 归一化，因此白名单要写 POM 中声明的原始名称。
2. **黑名单优先**：一个构件声明多个许可证时，只要有一个命中黑名单即 `DENIED`，
   即使另一个许可证命中白名单。
3. **坐标覆盖整体替换全局列表**：命中坐标规则（精确 `group:name:version` 优先于
   `group:name`）的构件，其白名单、黑名单、`allowUnknown` 全部使用坐标规则内的配置，
   全局规则对该构件不再生效；坐标规则中没有声明的许可证不会被放行。
4. **未知许可证**：默认违规。可在全局或坐标规则中用 `allowUnknown true` 显式放行。
5. **门禁**：`failOnError = true`（默认）时，任何 `DENIED` 或未放行的 `UNKNOWN` 都会
   让 `checkLicenses` 抛出异常并以非零退出码结束；`failOnError = false` 时只告警，
   报告头部结果为 `PASSED WITH WARNINGS`。
6. **报告中的命中规则**形如 `global:allow:Apache License, Version 2.0`、
   `global:deny:GNU General Public License, version 3`、`global:allow-unknown`、
   `coordinate-rule:group:name:version:allow:...`，便于审计追溯。

### JSON 报告结构

```json
{
  "gateMode": "fail",
  "violations": 1,
  "components": [
    {
      "group": "com.example",
      "name": "some-lib",
      "version": "1.0.0",
      "licenses": [ { "name": "Apache License, Version 2.0", "url": "..." } ],
      "verdict": "ALLOWED",
      "matchedRule": "global:allow:Apache License, Version 2.0"
    }
  ]
}
```

无法识别许可证时 `licenses` 为 `["UNKNOWN"]`、`verdict` 为 `UNKNOWN`。

## 采集方式与缓存/增量

- 任务解析 `runtimeClasspath` 配置（包含全部传递依赖），只统计外部 Maven 模块构件；
  本项目自身的 project 依赖与本地文件依赖不在统计范围内。
- 许可证从构件的 Maven POM 元数据中读取，依次尝试：
  1. Gradle 解析得到的 POM 构件；
  2. 与 jar 同目录的 POM（`file://` 形式的本地 Maven 仓库布局）；
  3. Gradle 模块缓存中同版本目录下的 POM（`caches/modules-2/files-2.1/...`）；
  4. 本地 `~/.m2/repository` 中的 POM。
- POM 无法读取或其中没有 `<licenses>` 时，该构件保留在报告中并标记为 `UNKNOWN`。
- 任务正确声明了输入（运行时 classpath 文件集合 + 依赖/许可证元数据指纹 +
  规则快照）与输出（文本、JSON 两个报告文件）。输入未变时重复执行结果为
  `UP-TO-DATE`；增删依赖、POM 许可证元数据变化或修改 DSL 规则都会触发重新执行。

## 测试

```bash
./gradlew test
```

集成测试基于 Gradle TestKit（`LicenseCheckPluginFunctionalTest`），全部使用测试内
在磁盘上构造的**本地假 Maven 仓库**（jar + POM），并以 `--offline` 运行，不联网。
覆盖四个必需场景：

1. 合规通过：含传递依赖，全部命中白名单；并校验第二次执行 `UP-TO-DATE`；
2. 黑名单命中：任务失败、非零退出、报告为 `DENIED`；放宽为 `failOnError=false` 后
   仅告警并构建成功；
3. 未知许可证：默认失败且构件被标记为 `UNKNOWN`（不跳过）；告警模式通过；
   `allowUnknown=true` 时判定 `ALLOWED`；
4. 坐标覆盖：GAV 精确覆盖与 `group:name` 版本无关覆盖均生效，去掉覆盖后黑名单仍拦截。

## 已知限制

- 只读取构件自身 POM 中的 `<licenses>`；不解析父 POM（Maven 本身也不继承 license），
  不合并 `dependencyManagement`/BOM 信息，不读取 Gradle Module Metadata 中的
  许可证字段。
- 许可证按名称精确文本匹配，不做 SPDX 标识符归一化、不匹配 `<url>`、不识别
  多许可证之间的 AND/OR 关系；黑名单采「任一命中即拒绝」的保守策略。
- 统计范围为 `runtimeClasspath` 上有实际 jar 构件的模块；POM-only（如仅作 BOM/
  约束使用）的构件不会出现在报告中。
- 项目自身的 project 依赖与 `files(...)` 本地文件依赖不检查。
- 不扫描构件 jar 内部打包的第三方代码（fat jar/uber jar、shade 后的类与资源文件）。
- 插件面向 JVM（`java`/`java-library` 等）项目；应用于无 JVM 插件的工程时不会
  注册任务。
