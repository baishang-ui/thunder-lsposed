# 音乐卡片歌词滚动跳动修复（2026-10-26）

用户反馈音乐卡片歌词滚动持续乱跳。本次只修改宿主文字更新 Hook 及相关检查，没有修改独立 App UI。

## 根因与修复

宿主 `MediaTextSwitcher.c(text, false)` 会按当前文字视图 `f` 去重；动画进行中还会清理或更新待提交文字。原 Hook 对被流体云拥有的媒体文字一律改成 `c(text, true)`，该分支每次都会调用 `TextView.setText`。相同歌词被重复送达时，原生跑马灯因此反复重置。

现在，当宿主请求普通更新、当前文字视图可见且文字内容相同时，保留原调用参数，让宿主执行自身的去重与待提交文字处理。歌词真正变化时仍使用已有的直接提交分支。宿主显式要求直接提交、或当前视图不可见需要整理显示状态时，继续执行原来的清理行为。判断使用宿主当前视图与文字，不额外维护容易过期的歌词缓存。

## 回归与验证

- 新增回归在旧逻辑上于第一次重复歌词更新即失败，修复后通过。
- 47 项文字 Hook 断言通过，覆盖 20 次重复更新、歌词变化、第二个文字视图、动画待提交内容清理、显式直接提交与不可见视图处理。
- 全量 `scripts/check_effects.py` 通过，包括上一轮删除修复的 192 项断言、3019 项共享对象生命周期检查及多卡片回归。
- 文字渲染检查 46 项通过。
- PLQ110 / Android API 37 上，临时 instrumentation 使用真实 Android TextView 与实际 Hook 回调完成 33 项文字检查；连续重复更新没有触发新的文字变化回调，手动设置的 scrollX 与 selected 状态保持不变。胶囊像素检查 42 项、卡片祖先/输入检查 20 项也通过。
- Debug / Release 构建、128 项 JUnit 通过，0 失败、0 跳过；Release lint 无 Error，有 44 项 Warning。

设备检查使用模拟宿主更新分支与真实 Android 控件，未将新模块安装到 SystemUI，也未改变用户音乐播放状态。这不等同于新版本在实际音乐卡片上长时间播放的视觉复测。普通测试包安装曾超时；通过已获 root 的测试设备安装临时 probe 后完成检查，probe 已卸载，临时设备文件已清理。

## 本地产物与证据

- 签名安装包：`dist/thunder-cloud-lyrics-fix-20261026.apk`，附 SHA-256 文件。
- 修复前失败：`.local/cloud-lyrics-red.log`。
- 全量回归：`.local/cloud-lyrics-effects.log`。
- 文字渲染：`.local/cloud-lyrics-renderer.log`。
- 设备检查：`.local/cloud-lyrics-device-root.log`。
- 构建与检查：`.local/cloud-lyrics-build.log`。
