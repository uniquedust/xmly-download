package com.wgx.domain;

import lombok.Data;

/**
 * xm文件一些重要信息
 *
 * @author wgx
 * @date 2024/8/31
 */
@Data
public class XmInfo {
    //声音标题
    private String title;
    //专辑名称
    private String album;
    //作者
    private String artist;
    //声音id,对应的应该就是trackId
    private String trackNumber;
    // 这个测试基本为空,但还是按照人家js逻辑来吧...
    private String ISRC;
    //encode
    private String encodedBy;
    //音频所占字节数
    private Integer size;
    //id3标签所占字节数
    private Integer headerSize;
    private String encodingTechnology;
}
