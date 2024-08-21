package com.wgx;

import lombok.Data;

/**
 * 配置类
 *
 * @author wgx
 * @date 2024/8/20
 */
@Data
public class ConfigInfo {
    //保存路径
    private String savePath;
    //cookie
    private String cookie;

    private String sounds;

    private String album;

    private String partOfAlbum;
}
