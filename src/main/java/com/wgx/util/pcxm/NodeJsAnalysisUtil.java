package com.wgx.util.pcxm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.wgx.domain.XmInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 使用nodejs库解析xm文件
 * https://blog.csdn.net/m0_67087330/article/details/134601599
 *
 * @author wgx
 * @date 2024/8/30
 */
public class NodeJsAnalysisUtil {
    private static final Logger logger = LogManager.getLogger(NodeJsAnalysisUtil.class);

    public static XmInfo getParameter(String xmPath) {
        // Node.js 脚本的路径
        String scriptPath = NodeJsAnalysisUtil.class.getClassLoader().getResource("script/jsScript.js").getPath();
        scriptPath = scriptPath.startsWith("/") ? scriptPath.substring(1) : scriptPath;
        // 使用 ProcessBuilder 启动 Node.js 脚本
        try {
            ProcessBuilder pb = new ProcessBuilder("node", scriptPath, xmPath);
            Process process = pb.start();

            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            // 读取错误输出
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            // 读取错误输出
            while ((line = stdError.readLine()) != null) {
                System.out.println(line);
            }

            // 等待子进程结束
            int exitCode = process.waitFor();
            //代表成功
            if(exitCode ==0){
                return JSON.parseObject(output.toString(), XmInfo.class);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        XmInfo parameter = getParameter("d:/1.xm");
        System.out.println(parameter);
    }

}
