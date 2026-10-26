# C 盘标准清理计划（预计回收约 10.3G）

只清理**零风险、可自动重建**的缓存文件，不动任何个人数据、聊天记录、浏览器数据和系统组件。

## 清理项

1. **用户临时文件** `C:\Users\wgx\AppData\Local\Temp`（2.8G）
   删除其中所有内容；正被占用的文件（如正在运行的软件产生的）会自动跳过，不影响系统。

2. **npm 缓存（两处，共 3.2G）**
   - `C:\Users\wgx\AppData\Local\npm-cache`（2.3G）— 优先用 `npm cache clean --force` 官方方式清理
   - `C:\Users\wgx\AppData\Roaming\npm-cache`（856M，旧版 npm 的缓存位置）— 直接删除

3. **VS Code 扩展安装包缓存** `...\Roaming\Code\CachedExtensionVSIXs`（2.2G）
   只是已下载过的扩展 .vsix 安装包副本，扩展本身不受影响。

4. **TypeScript 类型缓存** `C:\Users\wgx\AppData\Local\Microsoft\TypeScript`（567M）— tsserver 自动下载的缓存，会按需重建。

5. **pip 缓存** `...\Local\pip\cache`（338M）— 用 `pip cache purge` 或直接删除。

6. **uv 缓存** `...\Local\uv\cache`（349M）— 用 `uv cache clean` 或直接删除。

7. **node-gyp 编译缓存** `...\Local\node-gyp`（144M）。

8. **崩溃转储** `...\Local\CrashDumps`（111M）— 仅用于事后排错的 dump 文件。

9. 顺手删掉用户目录下的 IDEA 崩溃日志 `java_error_in_idea*.log`（约 3M）。

## 不动的部分

Edge / 必剪 / 腾讯系 / 百度网盘 / WPS 缓存、回收站（仅 29M）、WinSxS 系统组件——均保持原样。

## 执行方式与验证

- 逐项删除，被占用的文件跳过并忽略报错；
- 清理前后各跑一次 `df -h /c` 对比，汇报实际释放的空间；
- 预计 C 盘可用空间从 6.7G 恢复到约 17G。