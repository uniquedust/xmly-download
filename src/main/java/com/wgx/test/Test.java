package com.wgx.test;

import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import com.wgx.Main;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 测试类
 * @author wgx
 * @date 2024/8/21
 */
public class Test {
    private final static String PATTERN = "sound/([0-9]+)";
    public static void main(String[] args) {
       /* String body = HttpRequest.get(Main.BASEURL + new Date().getTime()).
                form(Main.getBaseParamMap(259432946l)).addHeaders(Main.getHeaderMap(true, 259432946l)).execute().body();
        System.out.println(body);*/

        String body = HttpRequest.get(Main.ALBUMURL).form(Main.getParamMap("81880118")).addHeaders(Main.getHeaderMap(false, null,false)).execute().body();
        System.out.println(body);

   /*     String line ="https://www.ximalaya.com/sound/1724290971564";
        Pattern r = Pattern.compile(PATTERN);
        Matcher m = r.matcher(line);
        while (m.find()) {
            System.out.println(m.group(0));
            System.out.println(m.group(1));
            System.out.println(m.group(2));
            System.out.println(m.groupCount());
        }*/

        System.out.println(5/4);
        System.out.println(7/4);
    }
}
