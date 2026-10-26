package com.wgx.util.pcxm.one;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * pc客户端下载的xm文件解密工具(wasmer方案)
 * 解密流程参照 https://github.com/Diaoxiaozhang/Ximalaya-XM-Decrypt 的main.py
 *
 * @author wgx
 * @date 2026/3/30
 */
public class PCxmDecryptUtilOne {

    /**
     * 第一阶段: AES-256-CBC解密
     * 注意: 密文长度本身就是16的整数倍,python版解密后也不做去填充,所以这里用NoPadding,
     * 如果用PKCS5Padding,最后一块不是合法填充会直接抛BadPaddingException
     */
    public static byte[] CbcDecrypt(byte[] encryptedData, String key, String iv) {
        try {
            //需要将iv转为字节(TSRC或TENC里存的是16进制的iv)
            byte[] ivByte = parseHex(iv);
            byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(bytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivByte);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            throw new RuntimeException("第一阶段AES-CBC解密失败", e);
        }
    }

    /**
     * 截取可显示ascii字符前缀,对应python的get_printable_bytes
     * 因为AES解密后最后一块是不完整的垃圾数据,遇到不可显示字符就截断
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
        byte[] result = new byte[count];
        System.arraycopy(data, 0, result, 0, count);
        return result;
    }

    /**
     * 第二阶段: 调用wasm还原base64,对应js里的window.xmDecrypt
     * <p>
     * js原实现是把数据逐字节塞进wasm线性内存,遇到ascii>127的字符就截断,
     * 所以这里要先对data做可显示字符过滤
     */
    public static String xmDecrypt(byte[] data, String trackId) {
        data = getPrintableBytes(data);
        byte[] trackIdBytes = trackId.getBytes(StandardCharsets.UTF_8);
        //栈指针,g函数会把返回结果(字符串指针+长度)写到这个位置
        int stackPointer = WasmerUtil.a(-16);
        //分配内存并写入数据
        int dataOffset = WasmerUtil.c(data.length);
        int trackIdOffset = WasmerUtil.c(trackIdBytes.length);
        //c函数可能触发内存扩容,每次写之前都要重新拿buffer
        ByteBuffer buffer = WasmerUtil.i();
        buffer.position(dataOffset);
        buffer.put(data);
        buffer = WasmerUtil.i();
        buffer.position(trackIdOffset);
        buffer.put(trackIdBytes);
        //调用g函数
        WasmerUtil.g(stackPointer, dataOffset, data.length, trackIdOffset, trackIdBytes.length);
        //读取返回结果: 栈指针处是一个i32结构(结果字符串的指针,长度,0,0)
        ByteBuffer resultBuffer = WasmerUtil.i();
        int resultPointer = resultBuffer.getInt(stackPointer);
        int resultLength = resultBuffer.getInt(stackPointer + 4);
        byte[] resultBytes = new byte[resultLength];
        resultBuffer.position(resultPointer);
        resultBuffer.get(resultBytes);
        return new String(resultBytes, StandardCharsets.UTF_8);
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
