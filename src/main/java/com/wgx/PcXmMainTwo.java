package com.wgx;

import com.wgx.domain.XmInfo;
import com.wgx.util.DataUtil;
import com.wgx.util.pcxm.two.PCxmDecryptUtilTwo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * 针对pc下载的xm文件进行解密(方案二: 纯java解析ID3 + Chicory运行wasm,需要JDK11+)
 * <p>
 * 功能与python版 https://github.com/Diaoxiaozhang/Ximalaya-XM-Decrypt 对齐:
 * 支持单个文件解密和文件夹批量解密(.xm),可自定义输出路径,
 * 输出文件按魔数识别音频格式,并重写标题/专辑/作者标签。
 * 不再依赖nodejs,整个流程纯java完成。
 *
 * @author wgx
 * @date 2026/10/24
 */
public class PcXmMainTwo {

    private static final Logger logger = LogManager.getLogger(PcXmMainTwo.class);

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("欢迎使用喜马拉雅音频解密工具(Java版)");
            System.out.println("本工具仅供学习交流使用，严禁用于商业用途");
            System.out.println("请选择您想要使用的功能：");
            System.out.println("1. 解密单个文件");
            System.out.println("2. 批量解密文件夹");
            System.out.println("3. 退出");
            String choice = scanner.nextLine().trim();
            List<String> filesToDecrypt;
            if ("1".equals(choice) || "2".equals(choice)) {
                if ("1".equals(choice)) {
                    System.out.println("请输入xm文件路径：");
                    String path = scanner.nextLine().trim().replace("\"", "");
                    if (!Files.isRegularFile(Paths.get(path))) {
                        System.out.println("文件不存在，请重新选择！");
                        continue;
                    }
                    filesToDecrypt = Collections.singletonList(path);
                } else {
                    System.out.println("请输入要批量解密的文件夹路径：");
                    String dir = scanner.nextLine().trim().replace("\"", "");
                    File[] xmFiles = new File(dir).listFiles((d, name) -> name.toLowerCase().endsWith(".xm"));
                    if (xmFiles == null || xmFiles.length == 0) {
                        System.out.println("该文件夹下没有找到xm文件，请重新选择！");
                        continue;
                    }
                    filesToDecrypt = new ArrayList<>();
                    for (File xmFile : xmFiles) {
                        filesToDecrypt.add(xmFile.getAbsolutePath());
                    }
                    System.out.println("共找到" + filesToDecrypt.size() + "个xm文件");
                }
                System.out.println("请选择是否需要设置输出路径：（不设置默认为本程序目录下的output文件夹）");
                System.out.println("1. 设置输出路径");
                System.out.println("2. 不设置输出路径");
                String outputChoice = scanner.nextLine().trim();
                String outputPath = "output";
                if ("1".equals(outputChoice)) {
                    System.out.println("请输入输出路径：");
                    outputPath = scanner.nextLine().trim().replace("\"", "");
                }
                for (String file : filesToDecrypt) {
                    try {
                        decryptXmFile(file, outputPath);
                    } catch (Exception e) {
                        logger.error("解密'{}'失败:{}", file, e.getMessage());
                        logger.debug(e.getMessage(), e);
                    }
                }
            } else if ("3".equals(choice)) {
                return;
            } else {
                System.out.println("输入错误，请重新输入！");
            }
        }
    }

    /**
     * 解密单个xm文件并按 专辑/标题.扩展名 保存
     */
    private static void decryptXmFile(String fromFile, String outputPath) throws Exception {
        logger.info("正在解密{}", fromFile);
        byte[] data = Files.readAllBytes(Paths.get(fromFile));
        //解析id3标签
        XmInfo info = PCxmDecryptUtilTwo.parseXmInfo(data);
        //三阶段解密
        byte[] audioData = PCxmDecryptUtilTwo.decrypt(data, info);
        //按魔数确定扩展名并保存
        String albumDir = outputPath + "/" + DataUtil.replaceInvalidChars(info.getAlbum());
        Files.createDirectories(Paths.get(albumDir));
        String output = albumDir + "/" + DataUtil.replaceInvalidChars(info.getTitle()) + "." + DataUtil.findExt(audioData);
        Files.write(Paths.get(output), audioData);
        //重写标签(标题/专辑/作者)
        writeTags(output, info);
        logger.info("解密成功，文件保存至{}！", output);
    }

    /**
     * 重写音频标签,对应python里mutagen的部分
     */
    private static void writeTags(String filePath, XmInfo info) {
        try {
            AudioFile audioFile = AudioFileIO.read(new File(filePath));
            Tag tag = audioFile.getTagOrCreateAndSetDefault();
            tag.setField(FieldKey.TITLE, info.getTitle());
            tag.setField(FieldKey.ALBUM, info.getAlbum());
            tag.setField(FieldKey.ARTIST, info.getArtist());
            audioFile.commit();
        } catch (Exception e) {
            logger.warn("音频标签写入失败:{},不影响解密结果", e.getMessage());
        }
    }

}
