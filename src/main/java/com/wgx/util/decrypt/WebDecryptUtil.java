package com.wgx.util.decrypt;

import cn.hutool.core.util.StrUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * web端
 * 根据js写的java解密
 * 简单总结下,虽然java的byte是带符号位的,但是其底层的二进制还是一样的,这边的解密基本都是对二进制进行操作
 *
 * @author wgx
 * @date 2024/8/19
 */
public class WebDecryptUtil {

    private static final byte[] longArray = new byte[]{(byte) 183, (byte) 174, 108, 16, (byte) 131, (byte) 159, (byte) 250, 5, (byte) 239, 110, (byte) 193, (byte) 202, (byte) 153, (byte) 137, (byte) 251, (byte) 176, 119, (byte) 150, 47, (byte) 204, 97, (byte) 237, 1, 71, (byte) 177, 42, 88, (byte) 218, (byte) 166, 82, 87, 94, 14, (byte) 195, 69, 127, (byte) 215, (byte) 240, (byte) 225, (byte) 197, (byte) 238, (byte) 142, 123, 44, (byte) 219, 50, (byte) 190, 29, (byte) 181, (byte) 186, (byte) 169, 98, (byte) 139, (byte) 185, (byte) 152, 13, (byte) 141, 76, 6, (byte) 157, (byte) 200, (byte) 132, (byte) 182, 49, 20, 116, (byte) 136, 43, (byte) 155, (byte) 194, 101, (byte) 231, (byte) 162, (byte) 242, (byte) 151, (byte) 213, 53, 60, 26, (byte) 134, (byte) 211, 56, 28, (byte) 223, 107, (byte) 161, (byte) 199, 15, (byte) 229, 61, 96, 41, 66, (byte) 158, (byte) 254, 21, (byte) 165, (byte) 253, 103, 89, 3, (byte) 168, 40, (byte) 246, 81, 95, 58, 31, (byte) 172, 78, 99, 45, (byte) 148, (byte) 187, (byte) 222, 124, 55, (byte) 203, (byte) 235, 64, 68, (byte) 149, (byte) 180, 35, 113, (byte) 207, 118, 111, 91, 38, (byte) 247, (byte) 214, 7, (byte) 212, (byte) 209, (byte) 189, (byte) 241, 18, 115, (byte) 173, 25, (byte) 236, 121, (byte) 249, 75, 57, (byte) 216, 10, (byte) 175, 112, (byte) 234, (byte) 164, 70, (byte) 206, (byte) 198, (byte) 255, (byte) 140, (byte) 230, 12, 32, 83, 46, (byte) 245, 0, 62, (byte) 227, 72, (byte) 191, (byte) 156, (byte) 138, (byte) 248, 114, (byte) 220, 90, 84, (byte) 170, (byte) 128, 19, 24, 122, (byte) 146, 80, 39, 37, 8, 34, 22, 11, 93, (byte) 130, 63, (byte) 154, (byte) 244, (byte) 160, (byte) 144, 79, 23, (byte) 133, 92, 54, 102, (byte) 210, 65, 67, 27, (byte) 196, (byte) 201, 106, (byte) 143, 52, 74, 100, (byte) 217, (byte) 179, 48, (byte) 233, 126, 117, (byte) 184, (byte) 226, 85, (byte) 171, (byte) 167, 86, 2, (byte) 147, 17, (byte) 135, (byte) 228, (byte) 252, 105, 30, (byte) 192, (byte) 129, (byte) 178, 120, 36, (byte) 145, 51, (byte) 163, 77, (byte) 205, 73, 4, (byte) 188, 125, (byte) 232, 33, (byte) 243, 109, (byte) 224, 104, (byte) 208, (byte) 221, 59, 9};
    private static final byte[] shortArray = new byte[]{(byte) 204, 53, (byte) 135, (byte) 197, 39, 73, 58, (byte) 160, 79, 24, 12, 83, (byte) 180, (byte) 250, 101, 60, (byte) 206, 30, 10, (byte) 227, 36, 95, (byte) 161, 16, (byte) 135, (byte) 150, (byte) 235, 116, (byte) 242, 116, (byte) 165, (byte) 171};

    public static String getSoundCryptLink(String link) {
        if (StrUtil.isEmpty(link)) {
            return "";
        }

        link = link.replace("_", "/").replace("-", "+");
        byte[] encode = Base64.getDecoder().decode(link.getBytes(StandardCharsets.UTF_8));
        //获取link除最后16位的字段
        byte[] rData = new byte[encode.length - 16];
        if (encode.length - 16 >= 0) System.arraycopy(encode, 0, rData, 0, encode.length - 16);
        //获取link最后16位的字段
        byte[] lastData = new byte[16];
        System.arraycopy(encode, encode.length - 16, lastData, 0, 16);

        //替换byte,这块卡了好久,因为java的byte是有符号的,一开始没注意,需要转成不带符号的
        for (int i = 0; i < rData.length; i++) {
            int temp = Byte.toUnsignedInt(rData[i]);
            rData[i] = longArray[temp];
        }
        for (int i = 0; i < rData.length; i += 16) {
            xorBlock(rData, i, lastData);
        }
        for (int i = 0; i < rData.length; i += 32) {
            xorBlock(rData, i, shortArray);
        }
        return new String(rData, StandardCharsets.UTF_8);

    }

    private static void xorBlock(byte[] rData, int offset, byte[] nData) {
        int min = Math.min(rData.length - offset, nData.length);
        for (int i = 0; i < min; i++) {
            rData[offset + i] = (byte) (rData[offset + i] ^ nData[i]);
        }
    }

    public static void main(String[] args) {
        String decryptedLink = getSoundCryptLink("27VGaioRgUehRlvGmFysmBwC-Gu3mZbfm_jOfha051ZHu9m3I0VG3qFBM_OE4OFX3nP2ZI4soi_AGOF-Hpy1zpt367fr8ZVHI7czQc8vXkGbvEuRbeX8aw_2dtwgyRmPmcq56-tn7DKhiv6E8zrFQRwzZTfRzg5r1-_OgZOS8MIIQJ8rP11UJbNm6wdOtZLGD89gH5FTYFJ6bJyVTie5IQi8K54iqfVVudmkyqZp8eA1UQEa_tAc0jDeqJ-tnPA2bLXY9_lF0eVzt46x8Bd9H-XI-ODHvVWmRhGWjy_lM2gACEFmncjYAw");
        System.out.println("Decrypted link: " + decryptedLink);
    }

}
