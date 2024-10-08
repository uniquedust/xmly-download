package com.wgx.domain;

import lombok.Data;

/**
 * 配置类实体
 *
 * @author wgx
 * @date 2024/8/20
 */
@Data
public class ConfigInfo {
    private Boolean isPc;

    private String savePath;

    private String cookie;

    private String sounds;

    private String album;

    private String partOfAlbum;

    private Boolean useAutoNumber;
}
