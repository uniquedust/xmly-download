package com.wgx.util.pcxm.two;

import com.dylibso.chicory.runtime.Memory;
import com.wgx.domain.XmInfo;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * pc客户端下载的xm文件解密工具(Chicory方案)
 * <p>
 * 完整复刻 https://github.com/Diaoxiaozhang/Ximalaya-XM-Decrypt 的main.py三阶段解密:
 * 第一阶段 AES-256-CBC 解密音频数据;
 * 第二阶段 调用wasm把打乱的base64还原成标准base64;
 * 第三阶段 与TSSE帧拼接后base64解码得到音频前半部分,再拼上文件尾部的原始数据。
 *
 * @author wgx
 * @date 2026/10/24
 */
public class PCxmDecryptUtilTwo {

    /**
     * 第一阶段AES-256-CBC的密钥
     */
    private static final String AES_KEY = "ximalayaximalayaximalayaximalaya";

    /**
     * 解析xm文件的ID3标签
     */
    public static XmInfo parseXmInfo(byte[] data) {
        return XmId3Parser.parse(data);
    }

    /**
     * 完整解密,返回最终的音频字节
     */
    public static byte[] decrypt(byte[] data) {
        return decrypt(data, parseXmInfo(data));
    }

    /**
     * 完整解密(外部已解析过标签时复用,避免重复解析)
     */
    public static byte[] decrypt(byte[] data, XmInfo info) {
        //iv优先取TSRC(ISRC),没有则取TENC
        String iv = info.getISRC() != null && !info.getISRC().isEmpty()
                ? info.getISRC() : info.getEncodedBy();
        //取出加密的音频部分字节(略过id3标签)
        byte[] encrypted = Arrays.copyOfRange(data, info.getHeaderSize(), info.getHeaderSize() + info.getSize());
        //第一阶段
        byte[] stage1 = cbcDecrypt(encrypted, AES_KEY, iv);
        //第二阶段
        String decryptStr = xmDecrypt(stage1, info.getTrackNumber());
        //第三阶段: TSSE里存的是原音频base64的开头一段,拼接后解码,再拼上文件尾部未加密的数据
        byte[] head = Base64.getMimeDecoder().decode(info.getEncodingTechnology() + decryptStr);
        byte[] tail = Arrays.copyOfRange(data, info.getHeaderSize() + info.getSize(), data.length);
        byte[] finalData = new byte[head.length + tail.length];
        System.arraycopy(head, 0, finalData, 0, head.length);
        System.arraycopy(tail, 0, finalData, head.length, tail.length);
        return finalData;
    }

    /**
     * 第一阶段: AES-256-CBC解密
     * 密文长度本身就是16的整数倍,python版解密后也不做去填充,所以用NoPadding
     */
    public static byte[] cbcDecrypt(byte[] encryptedData, String key, String iv) {
        try {
            //TSRC/TENC里存的是16进制的iv
            byte[] ivByte = parseHex(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(ivByte));
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            throw new RuntimeException("第一阶段AES-CBC解密失败", e);
        }
    }

    /**
     * 截取可显示ascii字符前缀,对应python的get_printable_bytes
     */
    public static byte[] getPrintableBytes(byte[] data) {
        int count = 0;
        for (byte b : data) {
            int c = b & 0xff;
            if (c < 0x20 || c > 0x7e) {
                break;
            }
            count++;
        }
        return Arrays.copyOfRange(data, 0, count);
    }

    /**
     * 第二阶段: 调用wasm还原base64,对应js里的window.xmDecrypt
     */
    public static String xmDecrypt(byte[] data, String trackId) {
        //js原实现是把数据逐字节塞进wasm线性内存,遇到ascii>127的字符就截断
        data = getPrintableBytes(data);
        byte[] trackIdBytes = trackId.getBytes(StandardCharsets.UTF_8);
        //栈指针,g函数会把返回结果(字符串指针+长度)写到这个位置
        int stackPointer = WasmRuntimeUtil.a(-16);
        //分配内存并写入数据
        int dataOffset = WasmRuntimeUtil.c(data.length);
        int trackIdOffset = WasmRuntimeUtil.c(trackIdBytes.length);
        WasmRuntimeUtil.i().write(dataOffset, data);
        WasmRuntimeUtil.i().write(trackIdOffset, trackIdBytes);
        //调用g函数
        WasmRuntimeUtil.g(stackPointer, dataOffset, data.length, trackIdOffset, trackIdBytes.length);
        //读取返回结果: 栈指针处是一个i32结构(结果字符串的指针,长度,0,0)
        Memory m = WasmRuntimeUtil.i();
        int resultPointer = m.readInt(stackPointer);
        int resultLength = m.readInt(stackPointer + 4);
        return new String(m.readBytes(resultPointer, resultLength), StandardCharsets.UTF_8);
    }

    /**
     * 十六进制到字节的转换 对应CryptoJS.enc.Hex.parse
     */
    private static byte[] parseHex(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

}
