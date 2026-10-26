package com.wgx.util;

import com.wgx.domain.ConfigInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;

/**
 * 工具类
 *
 * @author wgx
 * @date 2024/1/30 19:19
 */
public class DataUtil {
    private static final Logger logger = LogManager.getLogger(DataUtil.class);

    public static ConfigInfo getProperty() {
        ConfigInfo info = new ConfigInfo();
        //通过反射获取property中的配置
        Properties properties = new Properties();
        try {
            InputStream inputStream = DataUtil.class.getClassLoader().getResourceAsStream("application.properties");
            //设置字符集,否则中文会乱码
            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            properties.load(reader);
        } catch (IOException e) {
            logger.error("丢失配置文件application");
            logger.error(e.getMessage(), e);
        }
        info.setSavePath(properties.getProperty("savePath", "d:/test"));
        info.setCookie(properties.getProperty("cookie"));
        info.setPartOfAlbum(properties.getProperty("partOfAlbum"));
        info.setAlbum(properties.getProperty("album"));
        info.setSounds(properties.getProperty("sounds"));
        info.setIsPc(Boolean.parseBoolean(properties.getProperty("isPc", "false")));
        info.setUseAutoNumber(Boolean.parseBoolean(properties.getProperty("useAutoNumber", "true")));
        return info;
    }


    /**
     * 处理下文本值
     */
    public static String dealString(String title) {
        // 去除最后一个换行之后的内容,去除掉http后面的所有字符,去除掉文件名不支持的特殊字符
        title = title.replaceAll("[\\\\/:*?<>|]", "");
        //去除掉所有空格及换行
        return title.replaceAll(" +|\\n", "");
    }

    /**
     * 根据魔数判断解密后的音频格式,对应python里python-magic的部分
     */
    public static String findExt(byte[] audioData) {
        String head = new String(Arrays.copyOfRange(audioData, 0, Math.min(16, audioData.length)), StandardCharsets.ISO_8859_1);
        //m4a/mp4: 第4~8字节是ftyp
        if (head.length() >= 8 && head.substring(4, 8).equals("ftyp")) {
            return "m4a";
        }
        if (head.startsWith("fLaC")) {
            return "flac";
        }
        if (head.startsWith("RIFF") && head.length() >= 12 && head.substring(8, 12).equals("WAVE")) {
            return "wav";
        }
        //mp3: ID3头 或 帧同步字节0xFFEx
        if (head.startsWith("ID3") || (audioData.length >= 2 && (audioData[0] & 0xff) == 0xff && (audioData[1] & 0xe0) == 0xe0)) {
            return "mp3";
        }
        throw new RuntimeException("无法识别解密后的音频格式,前16字节: " + head);
    }

    /**
     * 处理文件名里不支持的特殊字符(替换为空格),对应python的replace_invalid_chars
     */
    public static String replaceInvalidChars(String name) {
        if (name == null || name.isEmpty()) {
            return "未知";
        }
        return name.replaceAll("[/\\\\:*?\"<>|]", " ").trim();
    }


}
