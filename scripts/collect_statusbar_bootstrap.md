# thunder 系统框架入口诊断

用于排查系统框架无报告、桌面逐帧 Hook 无连接，以及 r4 状态栏仍不跟随。这不是新功能安装包，无需再次重启或重复导出固件。

1. 将 `collect_statusbar_bootstrap.sh` 放到手机的下载目录。
2. 在手机终端执行：

   ```sh
   su -c 'sh /sdcard/Download/collect_statusbar_bootstrap.sh'
   ```

3. 将下载目录生成的 `thunder-statusbar-bootstrap-日期-时间-编号.tar.gz` 发回。

脚本只写入自己的采集目录与压缩包，不修改系统设置、模块配置或作用域，不清空日志、不重启进程。每条服务查询最多等待 20 秒；没有超时工具则跳过服务查询并保留提示。

采集内容包括版本及启动时间、thunder 安装信息与已安装 APK、thunder 自己的设置、系统框架和桌面动画运行回报 XML、system_server 中与模块/框架相关的内存映射，以及 LSPosed 当前/上次启动日志和 logcat 中匹配 thunder、系统入口或相关框架故障的行。每次匹配保留前 5 行、后 24 行，以保留异常堆栈；上下文可能包含其他模块或应用名称。不会复制完整日志、其他应用私有文件或整套固件。

`collection-status.txt` 汇总系统与桌面的启动、连接及首帧诊断和可找到的日志数量。没有匹配不能单独证明没有加载，也可能是日志轮转、日志关闭或路径不同。若找不到 LSPosed 日志，请用 LSPosed 管理器的日志导出功能导出当前日志一起发回。
