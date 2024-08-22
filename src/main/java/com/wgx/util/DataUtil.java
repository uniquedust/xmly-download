package com.wgx.util;

import com.wgx.ConfigInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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


}
