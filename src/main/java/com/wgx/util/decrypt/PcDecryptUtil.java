package com.wgx.util.decrypt;


import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
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



}
