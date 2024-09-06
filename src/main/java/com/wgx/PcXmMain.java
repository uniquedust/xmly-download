package com.wgx;


import cn.hutool.core.util.StrUtil;
import com.wgx.domain.ConfigInfo;
import com.wgx.domain.XmInfo;
import com.wgx.util.DataUtil;
import com.wgx.util.decrypt.PcDecryptUtil;
import com.wgx.util.pcxm.NodeJsAnalysisUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * 针对pc下载的xm文件进行解密
 *
 * @author wgx
 * @date 2024/8/30
 */
public class PcXmMain {
    private static final Logger logger = LogManager.getLogger(PcXmMain.class);

    private static final String key = "ximalayaximalayaximalayaximalaya";

    private static final ConfigInfo info;

    static {
        info = DataUtil.getProperty();
    }

    public static void main(String[] args) throws IOException {
        Path wasmPath = Paths.get("D:/1.xm");
        byte[] wasmBytes = Files.readAllBytes(wasmPath);
        //通过nodejs解析得到id3相关参数
        XmInfo xmInfo = NodeJsAnalysisUtil.getParameter("D:/1.xm");
        assert xmInfo != null;
        byte[] musicEncryptData = new byte[xmInfo.getSize()];
        //取出音频部分的字节(略过id3的字节,并加上实际音频的字节)
        System.arraycopy(wasmBytes, xmInfo.getHeaderSize(), musicEncryptData, 0, xmInfo.getSize());

        byte[] bytes = PcDecryptUtil.CbcDecrypt(musicEncryptData, key,
                StrUtil.isNotEmpty(xmInfo.getISRC()) ? xmInfo.getISRC() : xmInfo.getEncodedBy());
        String decryptStr = PcDecryptUtil.xmDecrypt(bytes,xmInfo.getTrackNumber());
        byte[] decode = Base64.getDecoder().decode(xmInfo.getEncodingTechnology() + decryptStr);
        byte[] totalData = new byte[decode.length+wasmBytes.length-xmInfo.getHeaderSize()-xmInfo.getSize()];
        System.arraycopy(decode, 0, totalData, 0, decode.length);
        System.arraycopy(wasmBytes, xmInfo.getHeaderSize()+xmInfo.getSize(), totalData, decode.length, totalData.length);
        String filePath = "output.m4a"; // 输出文件路径

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(totalData);
            System.out.println("文件已被创建并写入数据");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
