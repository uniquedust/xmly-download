package com.wgx.util.pc;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 使用nodejs库解析xm文件
 *
 * @author wgx
 * @date 2024/8/30
 */
public class NodeJsAnalysisUtil {
    public static void main(String[] args) {
        // Node.js 脚本的路径
        String scriptPath = "d:/test3.js";

        // 使用 ProcessBuilder 启动 Node.js 脚本
        try {
            ProcessBuilder pb = new ProcessBuilder("node", scriptPath);
            Process process = pb.start();

            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // 等待子进程结束
            int exitCode = process.waitFor();
            System.out.println("Node.js script exited with code: " + exitCode);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
