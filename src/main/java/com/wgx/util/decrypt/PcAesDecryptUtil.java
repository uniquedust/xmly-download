package com.wgx.util.decrypt;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Base64;

/**
 * pc端的参数解密工具
 * @author wgx
 * @date 2024/8/27
 */
public class PcAesDecryptUtil {
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
                    + Character.digit(hexString.charAt(i+1), 16));
        }
        return data;
    }


    public static void main(String[] args) {
        String test = "pX7rCko1ZPLJXbyU3qjcDqAp042BK5yCrhhNlUZEBd6lHKILemhbvHD1YkhQ7FDbR8oy0iWyTecaq-rqUqF4QgK6Yq71MGvfUu527Y6Lh3-pGhOMwCaqKFcKmAMjd_YwSWFWJPkA7IyMIkUFnFT6iFveD6nNPzeeFp_tLXcAcwjkaY7hzCkggIPQQYfi8_2YIIPhpEaBXz26c2lKq7vS72_pp9Vb9GgSgezuKF1AZhOj6HE0jqbh3vEXX-D9ixU5Tx7IsucyqYey-IeolUFJ5OpZuBgVHmSTF6IVtQea13s";
        System.out.println(decrypt(test, "aaad3e4fd540b0f79dca95606e72bf93"));
    }

}