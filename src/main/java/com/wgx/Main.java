package com.wgx;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.wgx.domain.ConfigInfo;
import com.wgx.util.*;
import com.wgx.util.decrypt.PcDecryptUtil;
import com.wgx.util.decrypt.WebDecryptUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 主程序,针对url进行下载
 *
 * @author wgx
 * @date 2024/8/20
 */
public class Main {
    //xmly主站
    public static final String XIMALAYAURL = "https://www.ximalaya.com";

    //获取用户信息url
    public static final String USERURL = "https://www.ximalaya.com/revision/my/getCurrentUserInfo";
    //获取专辑url
    public static final String ALBUMURL = "https://www.ximalaya.com/revision/album/v1/getTracksList";
    //获取pc专辑url
    public static final String PCALBUMURL = "https://pc.ximalaya.com/simple-revision-for-pc/album/v1/getTracksList";

    //获取声音信息url,后面只要带个数字就可以访问成功,但还是使用时间戳较好
    public static final String BASEURL = "https://www.ximalaya.com/mobile-playpage/track/v3/baseInfo/";
    //失败重试次数
    public static final int RETRYCOUNT = 3;
    //解析声音信息url正则
    private final static String PATTERN = "sound/([0-9]+)";
    //pc解密使用的secretKey
    private final static String SECRETKEY = "aaad3e4fd540b0f79dca95606e72bf93";


    private static final Logger logger = LogManager.getLogger(Main.class);

    private static final ConfigInfo info;

    static {
        info = DataUtil.getProperty();
    }

    public static void main(String[] args) {
        if (StrUtil.isEmpty(info.getCookie())) {
            logger.error("cookie不能为空");
            return;
        }
        //先检测cookie是否有效
        String baseBody = HttpRequest.get(USERURL).addHeaders(getHeaderMap(false, null, true)).execute().body();
        JSONObject jsonObject = JSONObject.parseObject(baseBody);
        String temp = jsonObject.getString("ret");
        if (HttpStatus.HTTP_OK != Integer.parseInt(temp)) {
            logger.error(jsonObject.getString("msg"));
            return;
        }

        logger.info("登录成功!欢迎用户:{}!", jsonObject.getJSONObject("data").getString("userName"));

        String album = info.getAlbum();
        if (StrUtil.isEmpty(album) && StrUtil.isEmpty(info.getSounds())) {
            logger.error("请填写要下载的东西");
            return;
        }
        long start = System.currentTimeMillis();
        if (StrUtil.isNotEmpty(album)) {
            //1、分页处理专辑数据
            Map<String, JSONArray> map = DealAlbum();
            //2、获取专辑每个数据基本信息
            Map<String, Map<String, String>> albumMap = getAlbum(map);
            logger.info("==========即将保存数据到本地,请耐心等待=======" );
            //3、解密及下载数据到本地
            assert albumMap != null;
            DownAlbum(albumMap);

        } else {
            //批量多个声音下载
            String[] split = info.getSounds().split(";");
            Pattern r = Pattern.compile(PATTERN);
            Map<String, Map<String, String>> albumMap = new HashMap<>();
            for (String trackId : split) {
                //获取下对应的trackid
                Matcher m = r.matcher(trackId);
                if (m.find()) {
                    trackId = m.group(1);
                }
                Long finalTrackId = Long.parseLong(trackId);
                int retry = 1;
                //失败则进行重试
                Map<String, String> soundMap;
                boolean flag = false;
                while (retry <= RETRYCOUNT) {
                    String body = HttpRequest.get(BASEURL + new Date().getTime()).
                            form(getBaseParamMap(finalTrackId)).addHeaders(getHeaderMap(true, finalTrackId, true)).execute().body();
                    //{"reqId":"07fbdb9b-64837146","ret":1001,"msg":"系统繁忙，请稍后再试!"}调用次数达到上限了
                    Integer ret = JSONObject.parseObject(body).getInteger("ret");
                    if (0 != ret) {
                        logger.error("声音'{}'获取时候报错:{},进行重试,当前第{}次重试", finalTrackId, JSONObject.parseObject(body).getString("msg"), retry);
                        retry++;
                        continue;
                    }
                    JSONArray playArray = JSONObject.parseObject(body).getJSONObject("trackInfo").getJSONArray("playUrlList");
                    //获取专辑名称
                    String albumTitle = JSONObject.parseObject(body).getJSONObject("albumInfo").getString("title");
                    albumTitle = DataUtil.dealString(albumTitle);
                    if (albumMap.containsKey(albumTitle)) {
                        soundMap = albumMap.get(albumTitle);
                    } else {
                        soundMap = new HashMap<>();
                        flag = true;
                    }
                    //声音名称
                    String title = JSONObject.parseObject(body).getJSONObject("trackInfo").getString("title");
                    title = DataUtil.dealString(title);
                    for (int j = 0; j < playArray.size(); j++) {
                        JSONObject palyObj = playArray.getJSONObject(j);
                        //测试的有四种音频 M4A_64 MP3_64 M4A_24 MP3_32
                        if (1 == palyObj.getInteger("qualityLevel") &&
                                (palyObj.getString("type").contains("128") || palyObj.getString("type").contains("64"))) {
                            soundMap.put(title, palyObj.getString("url"));
                            break;
                        }
                    }
                    retry = Integer.MAX_VALUE;
                    if (flag) {
                        albumMap.put(albumTitle, soundMap);
                    }
                }
            }
            DownAlbum(albumMap);
        }
        //需要关闭线程否则程序不会停止
        ThreadPool.shutdown();
        while (true) {// 等待所有任务都执行结束
            if (ThreadPool.getActiveCount() <= 0) {
                logger.info("====================总耗时：" + (System.currentTimeMillis() - start) / 1000 + "秒=================");
                break;
            }
        }
    }

    /**
     * 解密及下载数据到本地
     */
    private static void DownAlbum(Map<String, Map<String, String>> albumMap) {
        //使用多线程保存文件
        ThreadPoolExecutor threadPool = ThreadPool.getThreadPool();
        String savePath = info.getSavePath();
        for (Map.Entry<String, Map<String, String>> entry : albumMap.entrySet()) {
            String albumTitle = entry.getKey();
            File file = new File(savePath + File.separator + albumTitle);
            if (!file.exists()) {
                file.mkdirs();
            }
            Map<String, String> soundMap = entry.getValue();
            if (CollUtil.isEmpty(soundMap)) {
                logger.error("接口调用似乎已经达到上限,请明日再试!");
                return;
            }
            logger.info("专辑{}成功获取到{}条声音", albumTitle, soundMap.size());
            for (Map.Entry<String, String> sound : soundMap.entrySet()) {
                String soundName = sound.getKey();
                String soundCryptLink;
                if (info.getIsPc()) {
                    soundCryptLink = PcDecryptUtil.decrypt(sound.getValue(), SECRETKEY);
                } else {
                    soundCryptLink = WebDecryptUtil.getSoundCryptLink(sound.getValue());
                }
                String soundPath = savePath + File.separator + albumTitle + File.separator + soundName + ".m4a";
                if (!new File(soundPath).exists()) {
                    threadPool.submit(() -> {
                        HttpUtil.downloadFile(soundCryptLink, soundPath);
                    });
                }
            }
        }
    }

    /**
     * 获取专辑每个数据基本信息
     */
    private static Map<String, Map<String, String>> getAlbum(Map<String, JSONArray> map) {
        String partOfAlbum = info.getPartOfAlbum();
        int start = 0;
        int end = 0;
        if (StrUtil.isNotEmpty(partOfAlbum)) {
            String[] split1 = partOfAlbum.split("-");
            if (split1.length != 2) {
                logger.error("请检查partOfAlbum参数");
                return null;
            }
            start = Integer.parseInt(split1[0]) - 1;
            end = Integer.parseInt(split1[1]);
        }

        ThreadPoolExecutor threadPool = ThreadPool.getThreadPool();
        Map<String, Map<String, String>> returnMap = new HashMap<>();
        for (Map.Entry<String, JSONArray> entry : map.entrySet()) {
            String albumTitle = entry.getKey();
            JSONArray array = entry.getValue();
            if (start == 0 && end == 0) {
                end = array.size();
            }
            if (end > array.size()) {
                end = array.size();
            }
            Map<String, String> soundMap = new HashMap<>();
            for (int i = start; i < end; i++) {
                JSONObject jsonObject = array.getJSONObject(i);
                Long trackId = jsonObject.getLong("id");
                //每请求30个,睡上1s
                //todo 需要测试下客户端是根据频率限制还是总数来进行限制的
               /* if(i%30==0){
                    try {
                        System.out.println("睡1s");
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }*/
                threadPool.submit(() -> {
                    int retry = 1;
                    //失败则进行重试
                    while (retry <= RETRYCOUNT) {
                        String body = HttpRequest.get(BASEURL + new Date().getTime()).
                                form(getBaseParamMap(trackId)).addHeaders(getHeaderMap(true, trackId, true)).execute().body();
                        //{"reqId":"07fbdb9b-64837146","ret":1001,"msg":"系统繁忙，请稍后再试!"}调用次数达到上限了
                        Integer ret = JSONObject.parseObject(body).getInteger("ret");
                        if (0 != ret) {
                            logger.error("专辑'{}',声音'{}'获取时候报错:{},进行重试,当前第{}次重试", albumTitle, trackId, JSONObject.parseObject(body).getString("msg"), retry);
                            retry++;
                            continue;
                        }
                        JSONArray playArray = JSONObject.parseObject(body).getJSONObject("trackInfo").getJSONArray("playUrlList");
                        //声音名称
                        String title = JSONObject.parseObject(body).getJSONObject("trackInfo").getString("title");
                        title = DataUtil.dealString(title);
                        for (int j = 0; j < playArray.size(); j++) {
                            JSONObject palyObj = playArray.getJSONObject(j);
                            //测试的有四种音频 M4A_64 MP3_64 M4A_24 MP3_32 window用的是2
                            if ((1 == palyObj.getInteger("qualityLevel") || 2 == palyObj.getInteger("qualityLevel")) &&
                                    (palyObj.getString("type").contains("128") || palyObj.getString("type").contains("64"))) {
                                soundMap.put(title, palyObj.getString("url"));
                                break;
                            }
                        }
                        retry = Integer.MAX_VALUE;
                    }
                });
            }
            while (true) {// 等待所有任务都执行结束
                if (ThreadPool.getActiveCount() <= 0) {
                    logger.info("获取专辑'{}'每条数据基本信息完成", albumTitle);
                    break;
                }
            }
            returnMap.put(albumTitle, soundMap);
        }
        return returnMap;
    }

    /**
     * 分页处理专辑数据
     */
    private static Map<String, JSONArray> DealAlbum() {
        String temp = info.getAlbum();
        String[] split = temp.split(";");
        Map<String, Integer> map = new HashMap<>();
        Map<String, String> chineseMap = new HashMap<>();
        String albumUrl;
        if (info.getIsPc()) {
            albumUrl = PCALBUMURL;
        } else {
            albumUrl = ALBUMURL;
        }
        //获取专辑名称和声音数量
        for (String ablum : split) {
            String body = HttpRequest.get(albumUrl).form(getParamMap(ablum)).addHeaders(getHeaderMap(false, null, false)).execute().body();
            JSONObject jsonObject = JSON.parseObject(body);
            if (HttpStatus.HTTP_OK != Integer.parseInt(jsonObject.getString("ret"))) {
                logger.error("该专辑{}获取时候报错{}", ablum, jsonObject.getString("msg"));
                continue;
            }
            //该专辑的总数量
            Integer totalCount = jsonObject.getJSONObject("data").getInteger("trackTotalCount");
            String albumTitle = jsonObject.getJSONObject("data").getJSONArray("tracks")
                    .getJSONObject(0).getString("albumTitle");
            albumTitle = DataUtil.dealString(albumTitle);
            map.put(ablum, totalCount);
            chineseMap.put(ablum, albumTitle);
            logger.info("专辑<<{}>>查询完成,共{}条声音", albumTitle, totalCount);
        }

        //多个专辑的id和中文名
        /*
         * jsonarray格式,用这个主要是保证顺序,以便后边筛选自定义下载集数时候过滤
         * {
         *   "id":""//对应trackId
         * }
         * */
        Map<String, JSONArray> ablumSoundMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            String ablumId = entry.getKey();
            int totalCount = entry.getValue();

            int pageNum = (int) Math.ceil((double) totalCount / 100);
            JSONArray array = new JSONArray();
            for (int i = 0; i <= pageNum; i++) {
                String body = HttpRequest.get(albumUrl).form(getParamMap(ablumId, i + 1))
                        .addHeaders(getHeaderMap(false, null, false)).execute().body();
                JSONObject jsonObject = JSON.parseObject(body);
                if (HttpStatus.HTTP_OK != jsonObject.getInteger("ret")) {
                    logger.error("该专辑{}获取时候报错{}", ablumId, jsonObject.getString("msg"));
                }
                JSONArray trackArray = jsonObject.getJSONObject("data").getJSONArray("tracks");
                if (trackArray.size() > 0) {
                    for (int j = 0; j < trackArray.size(); j++) {
                        JSONObject tmp = new JSONObject();
                        Long trackId = trackArray.getJSONObject(j).getLong("trackId");
                        tmp.put("id", trackId);
                        array.add(tmp);
                    }
                }
            }
            ablumSoundMap.put(chineseMap.get(ablumId), array);
        }
        return ablumSoundMap;
    }


    /**
     * 构建头参数
     */
    public static Map<String, String> getHeaderMap(boolean isBase, Long id, boolean needCookie) {
        Map<String, String> map = new HashMap<>();
        map.put(Header.USER_AGENT.getValue(), UserAgentUtil.randomUserAgent());
        if (needCookie) {
            map.put(Header.COOKIE.getValue(), info.getCookie());
        }
        if (isBase) {
            map.put(Header.REFERER.getValue(), "https://www.ximalaya.com/sound/" + id);
        } else {
            map.put(Header.REFERER.getValue(), XIMALAYAURL);
        }
        return map;
    }

    /**
     * 构建传递参数,头一次查询专辑数据(获取专辑数量使用)
     */
    public static Map<String, Object> getParamMap(String album) {
        Map<String, Object> map = new HashMap<>();
        map.put("albumId", album);
        map.put("pageNum", 1);
        map.put("pageSize", 30);
        return map;
    }

    /**
     * 根据页数构建参数
     */
    public static Map<String, Object> getParamMap(String album, int pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("albumId", album);
        map.put("pageNum", pageNum);
        map.put("pageSize", 100);
        return map;
    }

    /**
     * 构建参数
     */
    public static Map<String, Object> getBaseParamMap(Long id) {
        Map<String, Object> map = new HashMap<>();
        if (info.getIsPc()) {
            map.put("device", "win");
            map.put("trackQualityLevel", 2);
        } else {
            map.put("device", "www2");
            map.put("trackQualityLevel", 1);
        }
        map.put("trackId", id);
        return map;
    }
}
