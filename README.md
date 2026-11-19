# ezadmin-scaffold

本项目提供 EzAdmin DSL 和原生 Spring Boot 两种管理系统开发方式。接到需求后，依次完成**设计、开发、测试验证**。

## 本机入口

应用默认监听 [localhost:8085](http://localhost:8085/)。首次启动创建 `admin`，初始密码使用 `EZADMIN_ADMIN_PASSWORD` 环境变量或 [application.yml](src/main/resources/application.yml) 的启动配置。配置为空时，随机密码写入项目的 `data/initial-admin-password.txt`。登录后可在右上角修改自己的密码。管理员可在「系统管理」新建、启停用户，分配角色、配置权限或重置密码。

系统权限基于用户、角色、用户角色及角色权限四张表，结构见 [schema.sql](src/main/resources/schema.sql)。内置管理员具备管理和资产读写权限，只读用户只具备资产查看权限。原生资产 CRUD 复用 `DEMO_ASSET`，示例路径为 `/native/assets.html`。

## 1. 结合现有库表做设计

- 明确用户角色、业务操作、正常流程、异常分支和验收条件。
- 对照 [初始化表结构](src/main/resources/schema.sql)、[初始数据](src/main/resources/data.sql) 和目标环境的实际库表。确认可复用的表、字段、主外键、唯一约束、索引及软删除规则。脚手架自带的 `ID` 直接复用。
- 为每个操作列出读表、写表和事务边界。设计需要新增的表或字段时，同时考虑已有数据如何升级。涉及权限和流程时，明确谁可以查看、修改或审批。
- 常规列表、表单和简单父子表优先用 DSL。一次提交写入超过 3 张业务表，或遇到复杂联动、多阶段校验、审批时，优先考虑原生代码。读表和联表展示不计入写表数量。列表常规而表单复杂时，可以保留 DSL 列表并打开原生表单。

## 2. 按设计开发

- DSL 沿用项目已有列表、表单和 Express `.ql` 的布局与约定。页面的 `appendHead` 默认加载 `ez-layout.css` 和 `ez-layout.js`，菜单在 [laynavs.json](src/main/resources/laynavs.json) 维护。
- 原生功能按 `controller / service / domain / mapper` 分层。Controller 接收请求，Service 管理校验和事务，Domain 表达业务数据，Mapper 使用 MyBatis 执行参数化 SQL。原生页面使用 Thymeleaf，审批与长流程使用 Camunda。
- 开发时只修改 DSL，可保存后刷新页面验证。Java、依赖、启动配置等发生变更时，重新构建并启动应用。

## 3. 测试与验收

- 从菜单或实际入口打开页面，验证查询、新增、编辑、删除等正常路径，再检查必填校验、越权访问和异常结果。
- 有多表写入时，验证提交后的各表状态，以及任一步失败后的回滚。涉及审批时，验证流程启动、任务处理和业务状态变更。
- 执行 `mvn -B verify` 跑集成测试。交付时记录实际验证过的命令、页面路径和结果。

默认账号：
admin/123456