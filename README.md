# xmly-download
用于下载喜马拉雅音频, 主要是参照该仓库进行开发的：https://github.com/Diaoxiaozhang/Ximalaya-Downloader

### 说明

- **下载**：填好配置文件里的cookie和专辑/声音id，直接运行Main.java即可，下载的是未加密的音频
- **解密**：当每日下载vip音频达到上限时，可以用pc客户端下载加密的xm文件，再用本工具解密（功能参照 https://github.com/Diaoxiaozhang/Ximalaya-XM-Decrypt 的python版复刻，详见下面的Java版本说明）

### xm文件解密 Java 版本说明

仓库里保留了两套等价实现，入口和包都分开方便对比：

| 方案 | 入口 | 包 | ID3解析 | wasm运行时 |
| ---- | ---- | ---- | ---- | ---- |
| 一(原思路修复版) | PcXmMainOne | com.wgx.util.pcxm.one | nodejs(node-id3) | wasmer-java(JNI) |
| 二(推荐) | PcXmMainTwo | com.wgx.util.pcxm.two | 纯java | Chicory(纯java) |

两套的核心解密流程完全一致，都是三阶段：AES-256-CBC解密 → wasm还原base64 → base64解码并拼接尾部数据。

- **JDK 版本**：**不能使用 JDK 8**。方案二依赖 [Chicory](https://chicory.dev/) 在 JVM 上执行 `decrypt.wasm`，其依赖为 Java 11 字节码；请在 **JDK 11+**（推荐 11 或 17 LTS）下构建与运行，并将 `JAVA_HOME` 指向该 JDK（整个工程已从8升到11）。方案一本身兼容JDK8，但工程整体升了就一起用JDK 11+吧
- **方案一额外要求**：
    * 需要 node 环境，并在**项目根目录**执行 `npm install node-id3`（node从js脚本位置向上找node_modules）
    * `wasmer-jni-0.3.0` jar没传到maven仓库，需要手动install到本地仓库（jar已放在resources下）：
    ```
    mvn install:install-file --settings D:\programFile\apache-maven-3.3.9\conf\settings.xml -Dpackaging=jar -DgroupId=com.test -DartifactId=wasmer-jni -Dversion=0.3.0 -Dfile=src/main/resources/wasmer-jni-amd64-windows-0.3.0.jar
    ```
    * wasmer-jni只支持amd64的windows/linux
- **方案二**：`com.dylibso.chicory:runtime` 纯java解释器，不依赖nodejs和本地dll，支持单个文件/批量文件夹解密、自定义输出路径、按魔数识别m4a/mp3/flac/wav、自动重写标题/专辑/作者标签，输出默认在程序目录下的`output/专辑名/标题.ext`
- **构建**：`mvn -q package`；命令行运行示例：
```
mvn -q compile dependency:copy-dependencies -DoutputDirectory=target/lib
java -cp "target/classes;target/lib/*" com.wgx.PcXmMainTwo
```

# 一、发布
* **2024/8/21**
    * 首次提交
        * 支持web端专辑下载，支持专辑选择对应音频下载
* **2024/8/22**
    * 支持多个自定义音频下载
* **2024/8/29**
    * 支持使用pc客户端的url及cookie进行下载，但是同样会有下载次数限制，似乎也是600集左右，弄完客户端直接没法使用，切换音频一直报系统繁忙
* **2024/9/6**
    * **尝试解析xm文件,失败**,主要问题出在了解析WebAssembly上,有兴趣的可以看下(没兴趣的话可以直接把报错的类和pom对应引用删除掉即可,运行main.java)
    * 解析的话还是用python或者nodejs吧...
* **2024/9/9**
    * 支持自动编号下载
* **2026/10/24**
    * **补全xm文件解密**,找到之前WebAssembly报错的真正原因了:WasmerUtil里每个方法都重新instantiate了一个新实例,数据写进A实例的线性内存,调g函数时却在B实例上执行,内存不共享,所以一直报out of bounds
    * 方案一(原思路修复版): wasmer实例改成全局共享 + AES改NoPadding,实测能跑通了
    * 方案二(推荐): 参照python版完整复刻,纯java解析ID3 + Chicory跑wasm,不再依赖nodejs,支持单个/批量解密和标签重写
    * JDK从8升到11(Chicory要求)
    * 还是ai牛逼

# 二、配置
配置文件为application.properties,注意点都写在里面了，直接运行Main.java即可
（xm解密功能不需要配置文件，直接运行PcXmMainTwo按菜单操作即可）


# 三、问题 
## 1、没法解析xm文件 —— 已解决
之前找了半天,没法解析xm文件(下面两个库都不行),人家python和nodejs就可以直接解析......
```xml
    <dependency>
            <groupId>org.jaudiotagger</groupId>
            <artifactId>jaudiotagger</artifactId>
            <version>2.0.1</version>
        </dependency>
        <dependency>
            <groupId>com.mpatric</groupId>
            <artifactId>mp3agic</artifactId>
            <version>0.9.1</version>
        </dependency>
```
下面的回答是问的chatgpt的
Java无法读取你的.xm文件，而Python和Node.js可以，主要原因在于库的设计和使用的底层技术的差异。以下是几个可能的原因和分析：

1. 库的设计与兼容性
   Python的mutagen库：mutagen库是一个非常灵活的音频元数据处理库，支持多种文件格式，并且其解析机制可能更加宽松，允许它忽略文件格式的某些不兼容之处，只要它能找到类似ID3标签的部分，它就能解析出来。
   Node.js的node-id3库：同样地，node-id3库可能具备类似的宽松解析能力，能够在文件中查找并处理ID3标签，而不对文件格式进行严格的验证。
   Java的Jaudiotagger库：Jaudiotagger的设计目标是严格支持常见的音频格式（如MP3、FLAC等），并且在加载文件时会验证文件格式。如果文件不符合它的预期格式，它将无法解析，甚至可能拒绝处理。
2. 底层文件处理机制
   Python与Node.js：这些语言中的库通常使用高度动态的文件处理机制，允许更灵活的字节流操作。mutagen和node-id3可能会直接处理字节流，而不严格依赖文件扩展名或文件头标识符。这使它们能够在更广泛的文件类型中找到ID3标签，甚至在不标准或加密的文件中。

Java的文件I/O处理：Java的I/O处理通常更为结构化，许多音频库依赖于文件格式的预定义标识符。Jaudiotagger就是这种情况，它通常依赖于文件头来确认文件类型和格式，如果文件的头部信息不匹配，它就无法继续解析。

3. 自定义加密文件
   加密文件的处理：你的.xm文件是自定义加密文件，这意味着文件的标准格式已经被修改或掩盖。如果Python和Node.js库能够读取这些文件，可能是因为这些库能够在解密或解析时跳过不必要的检查。而Java的Jaudiotagger库严格依赖于标准的文件头信息，导致它在遇到非标准文件时无法解析。
 
**结论**：jaudiotagger失败确实是因为它会先校验文件是不是标准音频格式。xm的ID3标签由喜马拉雅客户端固定生成，只需要那几个文本帧，所以方案二里直接按ID3v2.3规范手写了一个纯java解析器(XmId3Parser)，不再依赖nodejs。

## 2、WebAssembly使用失败 —— 已解决
找了半天,找到一个jar包,还没有上传到maven仓库,只能下载下来,自己打包到本地仓库,jar包也上传了,在resources下面
https://github.com/wasmerio/wasmer-java
```xml
mvn install:install-file --settings 
D:\programFile\apache-maven-3.3.9\conf\settings.xml  
-Dpackaging=jar -DgroupId=com.test -DartifactId=wasmer-jni -Dversion=0.3.0 
-Dfile=d:\wasmer-jni-amd64-windows-0.3.0.jar

<dependency>
    <groupId>com.test</groupId>
    <artifactId>wasmer-jni</artifactId>
    <version>0.3.0</version>
</dependency>
```
参考
https://github.com/Diaoxiaozhang/Ximalaya-XM-Decrypt  
然后照着python的例子改写java,但是一直报错,暂时没找到原因,大概率原因就是这个jar包太旧了,没有办法....
```java
Exception in thread "main" java.lang.RuntimeException: RuntimeError: out of bounds memory access
    at <unnamed> (<module>[71]:0xcf2a)
    at <unnamed> (<module>[25]:0x7b4e)
    at <unnamed> (<module>[83]:0xddb1)
	at org.wasmer.Instance.nativeCallExportedFunction(Native Method)
	at org.wasmer.Exports.lambda$null$0(Exports.java:85)
	at com.wgx.util.decrypt.WasmerUtil.g(WasmerUtil.java:82)
	at com.wgx.util.decrypt.PcDecryptUtil.xmDecrypt(PcDecryptUtil.java:123)
	at com.wgx.PcXmMainOne.main(PcXmMain.java:49)
```
**结论**：跟jar包新旧没关系，是当时WasmerUtil里a/c/i/g每个方法都调用了getInstance()重新instantiate——数据写进A实例的线性内存，调g函数时却在B实例上执行，内存不共享，所以out of bounds。改成全局共享一个实例后wasmer-java也能正常跑通(方案一)。另外又找了纯java的Chicory做了一版(方案二)，不需要本地dll也不用手动install jar，推荐用这个。
