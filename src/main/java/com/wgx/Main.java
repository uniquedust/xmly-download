package com.wgx;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.wgx.util.DataUtil;
import com.wgx.util.SoundCryptUtil;
import com.wgx.util.ThreadPool;
import com.wgx.util.UserAgentUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 主程序
 *
 * @author wgx
 * @date 2024/8/20
 */
public class Main {
    //xmly主站
    private static final String XIMALAYAURL = "https://www.ximalaya.com";

    //获取用户信息url
    private static final String USERURL = "https://www.ximalaya.com/revision/my/getCurrentUserInfo";
    //获取专辑url
    private static final String ALBUMURL = "https://www.ximalaya.com/revision/album/v1/getTracksList";
    //获取声音信息url,后面只要带个数字就可以访问成功,但还是使用时间戳较好
    private static final String BASEURL = "https://www.ximalaya.com/mobile-playpage/track/v3/baseInfo/";
    //失败重试次数
    private static final int RETRYCOUNT = 3;

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
        String body = HttpRequest.get(USERURL).addHeaders(getHeaderMap(false, null)).execute().body();
        JSONObject jsonObject = JSONObject.parseObject(body);
        String ret = jsonObject.getString("ret");
        if (HttpStatus.HTTP_OK != Integer.parseInt(ret)) {
            logger.error(jsonObject.getString("msg"));
            return;
        }

        logger.info("登录成功!欢迎用户:{}!", jsonObject.getJSONObject("data").getString("userName"));

        String album = info.getAlbum();
        if (StrUtil.isEmpty(album) && StrUtil.isEmpty(info.getSounds())) {
            logger.error("请填写要下载的东西");
            return;
        }
        if (StrUtil.isNotEmpty(album)) {
            //1、分页处理专辑数据
            Map<String, JSONArray> map = DealAlbum();
            //2、获取专辑每个数据基本信息
            Map<String, Map<String, String>> albumMap = getAlbum(map);
            //3、解密及下载数据到本地
            // DownAlbum(albumMap);

        } else {

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
            new File(savePath + File.separator + albumTitle).mkdirs();
            Map<String, String> soundMap = entry.getValue();
            for (Map.Entry<String, String> sound : soundMap.entrySet()) {
                String soundName = sound.getKey();
                String soundCryptLink = SoundCryptUtil.getSoundCryptLink(sound.getValue());
                //threadPool.submit(() -> {
                    HttpUtil.downloadFile(soundCryptLink, savePath + File.separator + albumTitle + File.separator + soundName + ".m4a");
                //});
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
            start = Integer.parseInt(split1[0]);
            end = Integer.parseInt(split1[1]);
        }

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
                int retry = 1;
                JSONObject jsonObject = array.getJSONObject(i);
                Long trackId = jsonObject.getLong("id");

                //失败则进行重试
                while (retry <= RETRYCOUNT) {
                    String body = HttpRequest.get(BASEURL + new Date().getTime()).
                            form(getBaseParamMap(trackId)).addHeaders(getHeaderMap(true, trackId)).execute().body();
                    if (0 != JSONObject.parseObject(body).getInteger("ret")) {
                        logger.error("该声音{}获取时候报错{},进行重试,当前第{}次重试", trackId, jsonObject.getString("msg"), retry);
                        retry++;
                        continue;
                    }
                    JSONArray playArray = JSONObject.parseObject(body).getJSONObject("trackInfo").getJSONArray("playUrlList");
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
                    retry = -1;
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
        //获取专辑名称和声音数量
        for (String ablum : split) {
            String body = HttpRequest.get(ALBUMURL).form(getParamMap(ablum)).addHeaders(getHeaderMap(false, null)).execute().body();
            System.out.println(body);
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
                String body = HttpRequest.get(ALBUMURL).form(getParamMap(ablumId, i + 1))
                        .addHeaders(getHeaderMap(false, null)).execute().body();
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
    public static Map<String, String> getHeaderMap(boolean isBase, Long id) {
        Map<String, String> map = new HashMap<>();
        map.put(Header.USER_AGENT.getValue(), UserAgentUtil.randomUserAgent());
        map.put(Header.COOKIE.getValue(), info.getCookie());
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
     * 根据页数构建参数
     */
    public static Map<String, Object> getBaseParamMap(Long id) {
        Map<String, Object> map = new HashMap<>();
        map.put("device", "www2");
        map.put("trackId", id);
        //todo 不知道有没有2的
        map.put("trackQualityLevel", 1);
        return map;
    }
}
