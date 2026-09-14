# 本地持久化试用

在项目根目录运行：

```sh
node scripts/run-rnd-preview.mjs build
node scripts/run-rnd-preview.mjs start
node scripts/run-rnd-preview.mjs status
```

PC：http://127.0.0.1:5183/admin/

手机页面：http://127.0.0.1:5184/m/

后台健康检查：http://127.0.0.1:8082/actuator/health

本机试用账号：`rnd_director`（研发总监）、`rnd_engineer`（研发人员），默认密码均为 `123456`。这些是本地演示账号；服务仅绑定本机回环地址。

`start` 启动独立后台进程，退出当前终端后仍运行；子服务异常退出最多尝试重启3次。系统关机或重启后，需要再次运行 `start`，没有安装开机自启服务。

停止：`node scripts/run-rnd-preview.mjs stop`。更新时先停止、执行 `build`，再启动。

数据、附件与配方/SOP/核价成果保存在项目 `data/preview/`，不会随服务重启或重新构建清空。日志也在该目录。备份时先停止服务，再完整复制该目录；恢复时停止服务后恢复完整目录。不要只备份数据库而遗漏 `archive/`。

该环境使用H2文件数据库供本机研发试用。原 `local` 和 `test` 内存数据库仍保持隔离；旧内存服务中的数据不会自动迁入。正式多人部署仍需验证PostgreSQL环境、持久存储及账号配置。

## 实际流程验收

```sh
node scripts/verify-rnd-preview.mjs
```

仅访问上述专用本机端口，创建明确标记“试用验证”的内部样品，按真实权限依次完成需求、分发、接收、打样草稿、合格关键点确认、正式版本、配方/SOP生成下载、内部测试、包装确认和核价下载。生成的示例参数仅用于软件联调，不是生产工艺标准。

过程状态保存在 `data/preview/acceptance.json`；再次执行会沿用同一样品，并验证重启后正式版本及文件仍可读取，不重复提交已完成节点。样例主料：100 kg→90 kg→72 kg，分工序得率90%、80%，最终得率72%。

验收文件：`acceptance-formula.xlsx`、`acceptance-sop.docx`、`acceptance-pricing.xlsx`。真实文件仍由系统成果接口提供下载，这些副本仅供核对。

执行 `node scripts/verify-rnd-preview.mjs --comparison` 可创建第二个对比样品，验证同口径熟制得率80%与70%，相差10个百分点。状态另存 `acceptance-comparison.json`。

编号分配会检查数据库已有记录；需求审核、任务分发/接收在重启后会读取持久化数据。本地模式按单实例使用，不作为多实例并发部署方案。
