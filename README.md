# xmly-download
用于下载喜马拉雅音频, 主要是参照该仓库进行开发的：https://github.com/Diaoxiaozhang/Ximalaya-Downloader

# 发布
* **2024/8/21**
    * 首次提交
        * 支持web端专辑下载，支持专辑选择对应音频下载
* **2024/8/22**
    * 支持多个自定义音频下载
* **2024/8/29**
    * 支持使用pc客户端的url及cookie进行下载，但是同样会有下载次数限制，似乎也是600集左右，弄完客户端直接没法使用，切换音频一直报系统繁忙
# 配置
配置文件为application.properties,注意点都写在里面了，直接运行Main.java即可


# 遇到的问题,所以使用nodejs库和python库
找了半天,没法解析xm文件(这两都不行),人家python和nodejs就可以直接解析......
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
   加密文件的处理：你的.xm文件是自定义加密文件，这意味着文件的标准格式已经被修改或掩盖。如果Python和Node.js库能够读取这些文件，可能是因为这些库能够在解密或解析时跳过不必要的检查。而Java的Jaudiotagger库则严格依赖于标准的文件头信息，导致它在遇到非标准文件时无法解析。
