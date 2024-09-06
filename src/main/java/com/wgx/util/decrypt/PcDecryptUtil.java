package com.wgx.util.decrypt;


import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Base64;

/**
 * pc端的参数解密工具
 *
 * @author wgx
 * @date 2024/8/27
 */
public class PcDecryptUtil {
    /**
     * ECB解密
     */
    public static String decrypt(String encryptedData, String key) {
        try {
            byte[] bytes = parseHex(key);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(bytes, "AES");
            //IvParameterSpec ivSpec = StrUtil.isBlank(iv) ? null : new IvParameterSpec(iv.getBytes());
            cipher.init(Cipher.DECRYPT_MODE, keySpec, (AlgorithmParameterSpec) null);
            byte[] decrypted = cipher.doFinal(Base64.getUrlDecoder().decode(encryptedData));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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


    /**
     * CBC解密
     */
    public static byte[] CbcDecrypt(byte[] encryptedData, String key, String iv) {
        try {
            //需要将iv转为16进制
            byte[] ivByte = parseHex(iv);
            byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(bytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivByte);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 对应js中的window.xmDecrypt
     */
    public static String xmDecrypt(byte[] data, String trackId) {
        /**
         *  for (; a < r; a++) {
         *     const t = n.charCodeAt(a);
         *     if (t > 127) break;
         *     //d就是从wasm中拿到的内存数组，这个就是将其塞入到内存数组中
         *     //当有ascii>127的，后面的数组就不要了
         *     d[o + a] = t
         *   }
         */
        //因为以上代码,所以针对data,要先坐下过滤
        int j = 0;
        for (int i = 0; i < data.length; i++) {
            if(data[i]>126||data[i]<32){
                j = i;
                break;
            }
        }
        if(j!=0){
            byte[] temp = data;
            data = new byte[j];
            System.arraycopy(temp,0,data,0,j);
        }
        Integer stackPointer = WasmerUtil.a(-16);
        System.out.println("data.length:"+data.length);
        Integer dataOffset = WasmerUtil.c(data.length);
        System.out.println("dataOffset:"+dataOffset);
        byte[] trackIdBytes = trackId.getBytes(StandardCharsets.UTF_8);
        System.out.println("trackIdBytes.length:"+trackIdBytes.length);
        Integer trackIdOffset = WasmerUtil.c(trackIdBytes.length);
        System.out.println("--------");
        ByteBuffer buffer = WasmerUtil.i();
        System.out.println("trackIdOffset:"+trackIdOffset);
        System.out.println("buffer.capacity():"+buffer.capacity());
        System.out.println("stackPointer:"+stackPointer);
        System.out.println("--------");
        // 检查 dataOffset 是否在 buffer 容量范围内
     /*   if (dataOffset >= buffer.capacity()) {
            throw new IllegalArgumentException("dataOffset 超出了 buffer 的容量范围");
        }*/
        buffer.position(dataOffset);
        buffer.put(data);
        buffer.position(trackIdOffset);
        buffer.put(trackIdBytes);
        System.out.println(buffer.capacity());
        ByteBuffer buffer2 = WasmerUtil.i();
        System.out.println(buffer2.capacity());

        WasmerUtil.g(stackPointer,dataOffset,data.length,trackIdOffset,trackIdBytes.length);
        //这样就会将字节数组合并为4字节int数组
        IntBuffer intBuffer = buffer.asIntBuffer();
        intBuffer.position(dataOffset/4);
        int resultPointer = intBuffer.get(0);
        int resultLength = intBuffer.get(1);
        assert intBuffer.get(2) == 0;
        assert intBuffer.get(3) == 0;
        byte[] array = buffer.array();
        byte[] returnByte = new byte[resultLength];
        System.arraycopy(array,resultPointer,returnByte,0,resultLength);
        return new String(returnByte);
    }

    /**
     * 对应的d函数
     * 简单来说，把可显示的 text 值（也就是英文 + 数字 + 符号）写入到 r.i.buffer，并返回写入的地址 (offset) 和写入的长度 (length)
     */
    public static byte[] d(byte[] bytes, int offset, int length) {
        return null;
    }


/*
    public static void main(String[] args) {
        String test = "pX7rCko1ZPLJXbyU3qjcDqAp042BK5yCrhhNlUZEBd6lHKILemhbvHD1YkhQ7FDbR8oy0iWyTecaq-rqUqF4QgK6Yq71MGvfUu527Y6Lh3-pGhOMwCaqKFcKmAMjd_YwSWFWJPkA7IyMIkUFnFT6iFveD6nNPzeeFp_tLXcAcwjkaY7hzCkggIPQQYfi8_2YIIPhpEaBXz26c2lKq7vS72_pp9Vb9GgSgezuKF1AZhOj6HE0jqbh3vEXX-D9ixU5Tx7IsucyqYey-IeolUFJ5OpZuBgVHmSTF6IVtQea13s";
        System.out.println(decrypt(test, "aaad3e4fd540b0f79dca95606e72bf93"));
    }
*/


}