[中文](README.md) | [English](README.en.md)

<p><img src="frontend/public/brand/logo.jpg" width="180" alt="知华科技正式 LOGO"></p>

# CutoverFlow · 系统切换与回退演练台账

**知华科技公开源码学习版 0.1.0／非商业源码版** · 知华科技（上海如静知华信息科技有限公司） · [官网](https://www.zhuatech.cn/)

系统基于 Java 21／Spring Boot、Vue 3、MySQL 和 Flyway，提供方案冻结、任务依赖、独立核验与回退台账。

一个跨团队切换窗口里，备份核对、数据转换、入口切换和业务验收常由不同人员负责。CutoverFlow 将方案、任务依赖、执行证据和回退核验放在同一条可追踪流程里，面向信息化实施团队、迁移协调员和切换指挥人员。

这里记录人工执行和核验结论。系统不会连接生产主机、执行脚本、迁移数据库、修改路由、备份或恢复数据；登记成功不表示真实设施已经切换或恢复。实际操作仍由获授权人员按受控方案完成。

## 从方案到一次可核对的运行

```text
草稿任务及依赖 → 提交 → 指定指挥人独立放行 → 冻结方案
                                                ↓
                                      演练 → 全项独立核验 → 指挥验收
                                                ↓
                                      正式切换 → 全项独立核验 → 指挥验收
                                                │
                              失败或人工终止 ────┘
                                     ↓
                  阻断新任务 → 逆序回退已触达任务 → 独立核验 → 确认恢复
```

- 每项任务有执行人、独立核验人、预计时长、步骤、验收标准、回退步骤和前置任务。支持分支与汇合，拒绝循环、自引用和跨方案依赖。
- 编制员与指挥人独立；指挥人不能兼任任务执行人，执行与核验不能为同一账号。管理员也不能替代指定责任人。
- 所有前置任务**核验通过**后才可开工；执行证据提交后仍须独立核验。任务失败即阻断继续开工，不提供越过失败的强制放行。
- 正式切换必须有同一冻结方案的已验收演练；同一方案只允许一个未结运行。放行后的任务内容不可变更，调整方案需新建编号重新演练。
- 每次运行有独立凭证、任务状态和最长48小时（系统参数可进一步缩短）的执行期限。到期不能继续推进，仍可登记失败和启动回退。
- 已开始任务均须回退，包括失败任务；未开始任务无需回退。有未核验回退的后继任务时，不能回退其前置任务。原执行状态与证据保留，回退记录单独保存。

## 功能与边界

| 模块 | 已实现 |
| --- | --- |
| 用户业务端 | 本人方案、指挥待办、执行与核验任务；方案与运行详情、状态、历史和反馈 |
| 方案编制 | 本人草稿增删改查、任务增删改、依赖环检查、提交和退回、放行冻结与退役 |
| 执行台 | 演练与正式切换、依赖门禁、执行证据、独立通过或失败、逆序回退与恢复验收 |
| 查询与报告 | 数据库搜索、状态筛选、分页、创建时间/编号排序；按范围统计和运行JSON报告 |
| 后台管理 | 账号、角色、权限名称、登记菜单、部门、字典、系统参数；最后管理员和引用保护 |
| 安全与审计 | BCrypt、同源会话、CSRF、实时权限/数据范围、个人改密码、业务事件与操作审计 |

数据范围为 `ALL` 全部部门、`DEPARTMENT` 本部门、`SELF` 本人编制、指挥、执行或核验关联的方案。关联方案内的完整任务基线和运行对关联人员可读，执行动作仍校验具体指派。列表、详情、工作台、统计和报告使用相同范围。管理接口要求全范围管理员。

未实现：脚本自动执行、云服务或CI/CD集成、自动备份恢复、附件上传、消息通知、模板复制、跨组织租户隔离、电子签名、客观指标自动采集和移动端专用应用。无模型或第三方业务凭证要求，不使用付费AI。文字证据可引用受控记录编号，不承担电子存证或防篡改保证。

## 实际运行页面

截图来自独立可销毁验收环境中的虚构资料，不包含真实系统或客户数据。

| 登录 | 用户端工作台 |
| --- | --- |
| ![登录](docs/screenshots/login.jpg) | ![本人工作台](docs/screenshots/workbench.jpg) |

登录：会话认证。本人工作台：查看关联方案、执行与核验待办。

| 冻结方案与任务依赖 | 运行执行与回退 |
| --- | --- |
| ![切换方案](docs/screenshots/plan.jpg) | ![运行执行台](docs/screenshots/run.jpg) |

切换方案：查看冻结任务基线和依赖。执行台：记录指定人员的执行、核验和逆序回退证据。

| 后台账号管理 | 数据统计 |
| --- | --- |
| ![账号管理](docs/screenshots/accounts.jpg) | ![运行统计](docs/screenshots/dashboard.jpg) |

账号管理：维护部门、角色和启用状态。运行统计：汇总授权范围内的方案和运行状态。

| 角色与数据权限 |
| --- |
| ![角色权限](docs/screenshots/permissions.jpg) |

角色权限：配置接口权限与全部、部门、本人数据范围。

业务端与管理端使用同一登录入口，按权限显示导航。详细操作见[操作手册](docs/操作手册.md)。

## 启动独立学习环境

需要 Docker Engine / Docker Desktop 和 Compose v2，以及 Python 3.10+。本机开发使用 Java 21、Maven 3.9、Node.js 24.19.0、npm 11 和 MySQL 8.4（MySQL 8 系列）。

```sh
python3 scripts/init-env.py
docker compose config --quiet
docker compose up -d --build --wait
```

访问 [http://127.0.0.1:8118](http://127.0.0.1:8118)，健康检查 [http://127.0.0.1:8118/actuator/health](http://127.0.0.1:8118/actuator/health)。端口冲突时在本地 `.env` 修改 `WEB_PORT`，例如18118。

初始用户名为 `admin`，密码由脚本随机生成，保存在权限0600且Git忽略的 `.env` 的 `ADMIN_PASSWORD` 中。没有公共默认密码。脚本拒绝覆盖现有文件；空库创建总部、五类角色、权限、菜单、类别、参数和管理员，不创建切换成功或恢复事实。演示资料通过标为TEST的可销毁库验收脚本生成。

| 环境变量 | 用途 |
| --- | --- |
| `DATABASE_PASSWORD`、`MYSQL_ROOT_PASSWORD` | 独立MySQL账号与初始化管理密码，必填 |
| `ADMIN_PASSWORD` | 空库管理员强密码，必填；已有库重启不覆盖账号 |
| `WEB_PORT`、`BIND_ADDRESS` | 前端端口与地址，默认8118、127.0.0.1 |
| `COOKIE_SECURE` | 本地HTTP为false，正式HTTPS必须true |

字段见[.env.example](.env.example)。本机后端还可通过 `DATABASE_URL`、`DATABASE_USER`、`DATABASE_CATALOG` 连接独立数据库 `zhuatech_cutoverflow`。地址、账号和密码须由环境提供，不要提交实际值。

```sh
# 先创建独立MySQL数据库、账号，并配置上述环境变量。
cd backend
mvn -B spotless:check test spring-boot:run
```

另一个终端，在项目根目录：

```sh
cd frontend
npm ci
npm run dev
```

Vite默认本机5173代理后端8080。容器前端通过服务名代理后端，不向主机暴露MySQL和后端。配置、HTTPS、备份、恢复与升级见[部署说明](docs/部署说明.md)。

## 工程、持久化与升级

后端：Java 21 / Spring Boot 4.0.7 / Spring Security / JPA / Flyway / MariaDB Connector/J 3.5.10。前端：Vue 3.5.40 / Vite 8.1.5 / JavaScript / Lucide。MySQL 8.4、Docker Compose、非root Nginx。

```text
浏览器 → Nginx → Spring Boot → MySQL持久化卷
frontend/src/                 业务端、后台、表单与状态动作
backend/src/main/java/        认证、权限、管理及切换事务
backend/src/main/resources/db/migration/
  V1__identity.sql            账号、角色、菜单、部门、字典、审计
  V2__cutover.sql             方案、任务、依赖、运行、证据与事件
backend/src/test/             单元及HTTP集成测试
scripts/                      私有配置生成、实库验收、发布检查
docs/                         操作、架构接口、部署、安全测试与截图
```

`cutover_plan` 与 `cutover_step/step_dependency` 保存基线；`cutover_run/run_task` 保存每次运行与独立证据；`command_record` 绑定账号、路由和完整结构化输入的幂等指纹；`flow_event` 保存业务历史。写事务使用总部配置行锁及读已提交隔离，串行化写入后重新核对实时权限；修订号拒绝过期页面。不是分布式高吞吐引擎。

数据库由Flyway按版本建表，JPA只验证结构。已执行迁移不能修改；升级需备份、添加下一版本SQL并验证原库升级及恢复。首次运行不依赖开发电脑数据库。详见[架构、数据库与接口](docs/架构与接口.md)。

每方案最多100任务、200次运行；统计最多1000方案，管理目录最多10000条，超限明确拒绝；事件页面显示最近500条，完整数据库保留历史。单组织单实例学习部署，未针对大规模运营验收。

## 测试、安全与问题反馈

```sh
mvn -B -f backend/pom.xml spotless:check test package
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose config --quiet
docker compose build
python3 scripts/release-check.py
git diff --check
```

真实MySQL验收会写入明确标为TEST的虚构资料，**仅对可丢弃验收库**运行：

```sh
python3 scripts/smoke.py --base http://127.0.0.1:8118 --env .env --allow-test-writes
# 重启或独立恢复后，以对应地址重新登录并核对已验收和已回退的记录。
python3 scripts/smoke.py --base http://127.0.0.1:8118 --env .env --verify-persistence
```

镜像构建执行Maven测试，不跳过测试。H2自动化集成测试不能替代MySQL首次迁移、完整闭环与重启持久化验收。验证内容和安全限制见[测试与安全](docs/测试与安全.md)。

会话Cookie为HttpOnly/SameSite Strict；接口执行CSRF与实时角色校验，密码BCrypt12轮，连续8次失败后限制同IP与账号5分钟。部署到正式网络需HTTPS、Secure Cookie、可信数据库证书与主机名验证、访问限制和备份。默认本地MySQL `sslMode=trust` 加密但不核验证书链，正式环境应配置验证。修改密码、禁用账号或撤销权限会使旧会话后续请求失效。不得提交凭证、生产地址、客户资料或数据库备份。

健康失败先查看 `docker compose ps` 与对应服务日志，核对配置和迁移。`STALE_VERSION` 应刷新；无法开工核对前置任务核验，无法回退核对后继任务回退核验。不要删除真实数据卷处理故障。

问题请在仓库Issues附版本、脱敏步骤和预期结果；安全漏洞通过官网或微信私下反馈，不公开凭证或利用材料。贡献需有代码权利、保留署名与许可、提交小范围变更并运行相应检查，不接受客户代码或资料。[第三方声明](THIRD_PARTY_NOTICES.md)与各组件原许可独立适用。

软件按现状提供，使用者负责实际方案、执行权限、操作风险、数据安全、备份与适用规则。不存在客户案例、认证或上线保障承诺。

## 联系知华科技

本项目由知华科技（上海如静知华信息科技有限公司）提供公开源码学习版本，主要用于个人学习、技术研究与非商业交流。未经书面授权不得商用。企业信息化建设、中小企业数字化转型、中小企业 AI 转型、私有化部署、软件外包、软件项目外包、软件实施、FDE 外包、OPC 技术支持及深度定制开发，请访问知华科技官网 [https://www.zhuatech.cn/](https://www.zhuatech.cn/)，或添加微信 zhuatech、zhuatech2 咨询。

根目录[LICENSE](LICENSE)规定自有代码非商业授权。企业内部生产、收费部署、SaaS、商业交付、二次销售和商业培训须书面授权；本许可证不是OSI标准开源许可证。不得删除品牌、版权及联系方式。

- 公司：上海如静知华信息科技有限公司
- 官网：[www.zhuatech.cn](https://www.zhuatech.cn/)
- 商业授权、定制开发、部署与系统集成咨询微信：**zhuatech**、**zhuatech2**

<table><tr>
<td align="center"><img src="docs/images/wechat-zhuatech.png" height="210" alt="知华科技微信咨询 zhuatech"><br>微信 zhuatech</td>
<td align="center"><img src="docs/images/wechat-zhuatech2.png" height="210" alt="知华科技微信咨询 zhuatech2"><br>微信 zhuatech2</td>
</tr></table>

商业授权或深度定制开发请联系知华科技。
