package com.wgx.util.pc;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * ʹ��nodejs�����xm�ļ�
 *
 * @author wgx
 * @date 2024/8/30
 */
public class NodeJsAnalysisUtil {
    public static void main(String[] args) {
        // Node.js �ű���·��
        String scriptPath = "d:/test3.js";

        // ʹ�� ProcessBuilder ���� Node.js �ű�
        try {
            ProcessBuilder pb = new ProcessBuilder("node", scriptPath);
            Process process = pb.start();

            // ��ȡ���
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // �ȴ��ӽ��̽���
            int exitCode = process.waitFor();
            System.out.println("Node.js script exited with code: " + exitCode);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
