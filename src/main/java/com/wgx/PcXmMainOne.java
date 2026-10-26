package com.wgx;

import cn.hutool.core.util.StrUtil;
import com.wgx.domain.XmInfo;
import com.wgx.util.DataUtil;
import com.wgx.util.pcxm.one.NodeJsAnalysisUtil;
import com.wgx.util.pcxm.one.PCxmDecryptUtilOne;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;

/**
 * 针对pc下载的xm文件进行解密(方案一: nodejs解析ID3 + wasmer-java调用wasm)
 * <p>
 * 即最早的那套思路,保留了原有的整体流程,只修了两个问题:
 * 1. wasmer实例改为全局共享(之前每个方法里都重新instantiate,内存不共享导致out of bounds)
 * 2. AES由PKCS5Padding改为NoPadding(密文本就是16的整数倍,去填充反而会抛异常)
 * 注意: wasmer-jni只支持amd64的windows/linux,且需要先手动install到本地仓库(见README)
 *
 * @author wgx
 * @date 2024/8/30
 */
public class PcXmMainOne {

    private static final Logger logger = LogManager.getLogger(PcXmMainOne.class);

    //第一阶段AES-256-CBC的密钥
    private static final String KEY = "ximalayaximalayaximalayaximalaya";

    public static void main(String[] args) throws Exception {
        //支持通过启动参数传入xm文件路径,不传默认D:/1.xm
        String xmPath = args.length > 0 ? args[0] : "D:/1.xm";
        logger.info("正在解密{}", xmPath);
        byte[] data = Files.readAllBytes(Paths.get(xmPath));
        //通过nodejs解析得到id3相关参数
        XmInfo xmInfo = NodeJsAnalysisUtil.getParameter(xmPath);
        if (xmInfo == null) {
            logger.error("nodejs解析id3失败,请检查是否安装了node环境");
            return;
        }
        logger.info("id3解析完成: title={}, album={}, trackId={}, size={}, headerSize={}",
                xmInfo.getTitle(), xmInfo.getAlbum(), xmInfo.getTrackNumber(), xmInfo.getSize(), xmInfo.getHeaderSize());
        //取出加密的音频部分字节(略过id3标签,并截取实际音频的字节)
        byte[] musicEncryptData = Arrays.copyOfRange(data, xmInfo.getHeaderSize(), xmInfo.getHeaderSize() + xmInfo.getSize());
        //第一阶段AES-256-CBC解密
        String iv = StrUtil.isNotEmpty(xmInfo.getISRC()) ? xmInfo.getISRC() : xmInfo.getEncodedBy();
        byte[] stage1 = PCxmDecryptUtilOne.CbcDecrypt(musicEncryptData, KEY, iv);
        //第二阶段调用wasm还原base64
        String decryptStr = PCxmDecryptUtilOne.xmDecrypt(stage1, xmInfo.getTrackNumber());
        //第三阶段: TSSE拼接wasm返回的base64后解码,再拼上文件尾部的原始数据
        byte[] head = Base64.getMimeDecoder().decode(xmInfo.getEncodingTechnology() + decryptStr);
        byte[] tail = Arrays.copyOfRange(data, xmInfo.getHeaderSize() + xmInfo.getSize(), data.length);
        byte[] totalData = new byte[head.length + tail.length];
        System.arraycopy(head, 0, totalData, 0, head.length);
        System.arraycopy(tail, 0, totalData, head.length, tail.length);
        //输出到程序目录下的output文件夹
        String outputPath = "output/" + DataUtil.replaceInvalidChars(xmInfo.getAlbum()) + "/"
                + DataUtil.replaceInvalidChars(xmInfo.getTitle()) + "." + DataUtil.findExt(totalData);
        Files.createDirectories(Paths.get(outputPath).getParent());
        Files.write(Paths.get(outputPath), totalData);
        logger.info("解密成功，文件保存至{}！", outputPath);
    }

}
