package com.wgx.util.pcxm.two;

import com.wgx.domain.XmInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 纯java实现的ID3v2解析器(方案二)
 * <p>
 * xm文件的ID3标签由喜马拉雅客户端固定生成,只需要TIT2/TPE1/TALB/TRCK/TSRC/TENC/TSIZ/TSSE这几个文本帧,
 * 之前jaudiotagger解析失败是因为它会先校验文件是不是标准音频格式,这里直接按ID3v2.3规范解析字节,
 * 不依赖任何第三方库,也不再需要nodejs
 *
 * @author wgx
 * @date 2026/10/24
 */
public class XmId3Parser {
    private static final Logger logger = LogManager.getLogger(XmId3Parser.class);

    /**
     * 解析xm文件头部的ID3v2标签
     *
     * @param data xm文件完整字节
     * @return 标签信息,headerSize为整个标签(含10字节头和footer)占用的字节数
     */
    public static XmInfo parse(byte[] data) {
        if (data.length < 10 || data[0] != 'I' || data[1] != 'D' || data[2] != '3') {
            throw new RuntimeException("不是合法的xm文件,头部缺少ID3标签");
        }
        //主版本号(3或4)
        int major = data[3] & 0xff;
        int flags = data[5] & 0xff;
        //标签体大小(不含10字节头,syncsafe编码)
        int tagBodySize = syncsafe(data, 6);
        //footer标志位,有的话标签尾部还有10字节
        int headerSize = 10 + tagBodySize + ((flags & 0x10) != 0 ? 10 : 0);

        byte[] body = Arrays.copyOfRange(data, 10, 10 + tagBodySize);
        //v2.3的整标签非同步标志
        if ((flags & 0x80) != 0) {
            body = deUnsync(body);
        }

        Map<String, String> frames = new HashMap<>();
        int offset = 0;
        //v2.3扩展头: 4字节大小(不含自身); v2.4扩展头: syncsafe大小(含自身)
        if ((flags & 0x40) != 0 && body.length >= 4) {
            int extSize = major >= 4 ? syncsafe(body, 0) : u32(body, 0);
            offset += major >= 4 ? extSize : extSize + 4;
        }
        //逐帧解析
        while (offset + 10 <= body.length) {
            String frameId = new String(body, offset, 4, StandardCharsets.ISO_8859_1);
            //帧id必须是4个大写字母数字,否则说明进入填充区
            if (!frameId.matches("[A-Z0-9]{4}")) {
                break;
            }
            //v2.3帧大小是普通大端整数,v2.4是syncsafe
            int frameSize = major >= 4 ? syncsafe(body, offset + 4) : u32(body, offset + 4);
            if (frameSize <= 0 || offset + 10 + frameSize > body.length) {
                logger.warn("id3帧{}大小异常:{},停止解析", frameId, frameSize);
                break;
            }
            byte[] payload = Arrays.copyOfRange(body, offset + 10, offset + 10 + frameSize);
            //只关心文本帧
            if (frameId.charAt(0) == 'T') {
                frames.put(frameId, decodeText(payload));
            }
            offset += 10 + frameSize;
        }

        XmInfo info = new XmInfo();
        info.setTitle(frames.getOrDefault("TIT2", ""));
        info.setAlbum(frames.getOrDefault("TALB", ""));
        info.setArtist(frames.getOrDefault("TPE1", ""));
        info.setTrackNumber(firstNumber(frames.get("TRCK")));
        info.setISRC(frames.getOrDefault("TSRC", ""));
        info.setEncodedBy(frames.getOrDefault("TENC", ""));
        info.setEncodingTechnology(frames.getOrDefault("TSSE", ""));
        String tsiz = frames.get("TSIZ");
        if (tsiz == null) {
            throw new RuntimeException("xm文件缺少TSIZ帧(音频大小),无法解密");
        }
        info.setSize(Integer.parseInt(firstNumber(tsiz)));
        if (info.getISRC().isEmpty() && info.getEncodedBy().isEmpty()) {
            throw new RuntimeException("xm文件TSRC/TENC帧都为空,缺少解密iv");
        }
        info.setHeaderSize(headerSize);
        return info;
    }

    /**
     * 文本帧解码: 首字节是编码,0=ISO-8859-1 1=UTF-16(带BOM) 2=UTF-16BE 3=UTF-8
     * 文本可能包含多个以\0分隔的值,只取第一个
     */
    private static String decodeText(byte[] payload) {
        if (payload.length == 0) {
            return "";
        }
        int enc = payload[0] & 0xff;
        byte[] text = Arrays.copyOfRange(payload, 1, payload.length);
        String value;
        switch (enc) {
            case 1:
                value = new String(text, StandardCharsets.UTF_16);
                break;
            case 2:
                value = new String(text, StandardCharsets.UTF_16BE);
                break;
            case 3:
                value = new String(text, StandardCharsets.UTF_8);
                break;
            default:
                value = new String(text, StandardCharsets.ISO_8859_1);
        }
        int end = value.indexOf('\0');
        return end >= 0 ? value.substring(0, end) : value;
    }

    /**
     * TRCK/TSIZ可能是 3 或 3/12 这种格式,取第一段数字
     */
    private static String firstNumber(String text) {
        if (text == null) {
            return null;
        }
        int end = text.indexOf('/');
        return end >= 0 ? text.substring(0, end) : text;
    }

    /**
     * 去除非同步化(v2.3标签整体): FF 00 -> FF
     */
    private static byte[] deUnsync(byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.length);
        for (int i = 0; i < body.length; i++) {
            out.write(body[i]);
            if ((body[i] & 0xff) == 0xff && i + 1 < body.length && body[i + 1] == 0) {
                i++;
            }
        }
        return out.toByteArray();
    }

    /**
     * syncsafe整数(每个字节只取低7位)
     */
    private static int syncsafe(byte[] d, int off) {
        return ((d[off] & 0x7f) << 21) | ((d[off + 1] & 0x7f) << 14) | ((d[off + 2] & 0x7f) << 7) | (d[off + 3] & 0x7f);
    }

    /**
     * 普通大端u32
     */
    private static int u32(byte[] d, int off) {
        return ((d[off] & 0xff) << 24) | ((d[off + 1] & 0xff) << 16) | ((d[off + 2] & 0xff) << 8) | (d[off + 3] & 0xff);
    }

}
