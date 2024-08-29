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
import com.wgx.util.*;
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
 * ������
 *
 * @author wgx
 * @date 2024/8/20
 */
public class Main {
    //xmly��վ
    public static final String XIMALAYAURL = "https://www.ximalaya.com";

    //��ȡ�û���Ϣurl
    public static final String USERURL = "https://www.ximalaya.com/revision/my/getCurrentUserInfo";
    //��ȡר��url
    public static final String ALBUMURL = "https://www.ximalaya.com/revision/album/v1/getTracksList";
    //��ȡpcר��url
    public static final String PCALBUMURL = "https://pc.ximalaya.com/simple-revision-for-pc/album/v1/getTracksList";

    //��ȡ������Ϣurl,����ֻҪ�������־Ϳ��Է��ʳɹ�,������ʹ��ʱ����Ϻ�
    public static final String BASEURL = "https://www.ximalaya.com/mobile-playpage/track/v3/baseInfo/";
    //ʧ�����Դ���
    public static final int RETRYCOUNT = 3;
    //����������Ϣurl����
    private final static String PATTERN = "sound/([0-9]+)";
    //pc����ʹ�õ�secretKey
    private final static String SECRETKEY = "aaad3e4fd540b0f79dca95606e72bf93";


    private static final Logger logger = LogManager.getLogger(Main.class);

    private static final ConfigInfo info;

    static {
        info = DataUtil.getProperty();
    }

    public static void main(String[] args) {
        if (StrUtil.isEmpty(info.getCookie())) {
            logger.error("cookie����Ϊ��");
            return;
        }
        //�ȼ��cookie�Ƿ���Ч
        String baseBody = HttpRequest.get(USERURL).addHeaders(getHeaderMap(false, null, true)).execute().body();
        JSONObject jsonObject = JSONObject.parseObject(baseBody);
        String temp = jsonObject.getString("ret");
        if (HttpStatus.HTTP_OK != Integer.parseInt(temp)) {
            logger.error(jsonObject.getString("msg"));
            return;
        }

        logger.info("��¼�ɹ�!��ӭ�û�:{}!", jsonObject.getJSONObject("data").getString("userName"));

        String album = info.getAlbum();
        if (StrUtil.isEmpty(album) && StrUtil.isEmpty(info.getSounds())) {
            logger.error("����дҪ���صĶ���");
            return;
        }
        long start = System.currentTimeMillis();
        if (StrUtil.isNotEmpty(album)) {
            //1����ҳ����ר������
            Map<String, JSONArray> map = DealAlbum();
            //2����ȡר��ÿ�����ݻ�����Ϣ
            Map<String, Map<String, String>> albumMap = getAlbum(map);
            logger.info("==========�����������ݵ�����,�����ĵȴ�=======" );
            //3�����ܼ��������ݵ�����
            assert albumMap != null;
            DownAlbum(albumMap);

        } else {
            //���������������
            String[] split = info.getSounds().split(";");
            Pattern r = Pattern.compile(PATTERN);
            Map<String, Map<String, String>> albumMap = new HashMap<>();
            for (String trackId : split) {
                //��ȡ�¶�Ӧ��trackid
                Matcher m = r.matcher(trackId);
                if (m.find()) {
                    trackId = m.group(1);
                }
                Long finalTrackId = Long.parseLong(trackId);
                int retry = 1;
                //ʧ�����������
                Map<String, String> soundMap;
                boolean flag = false;
                while (retry <= RETRYCOUNT) {
                    String body = HttpRequest.get(BASEURL + new Date().getTime()).
                            form(getBaseParamMap(finalTrackId)).addHeaders(getHeaderMap(true, finalTrackId, true)).execute().body();
                    //{"reqId":"07fbdb9b-64837146","ret":1001,"msg":"ϵͳ��æ�����Ժ�����!"}���ô����ﵽ������
                    Integer ret = JSONObject.parseObject(body).getInteger("ret");
                    if (0 != ret) {
                        logger.error("����'{}'��ȡʱ�򱨴�:{},��������,��ǰ��{}������", finalTrackId, JSONObject.parseObject(body).getString("msg"), retry);
                        retry++;
                        continue;
                    }
                    JSONArray playArray = JSONObject.parseObject(body).getJSONObject("trackInfo").getJSONArray("playUrlList");
                    //��ȡר������
                    String albumTitle = JSONObject.parseObject(body).getJSONObject("albumInfo").getString("title");
                    albumTitle = DataUtil.dealString(albumTitle);
                    if (albumMap.containsKey(albumTitle)) {
                        soundMap = albumMap.get(albumTitle);
                    } else {
                        soundMap = new HashMap<>();
                        flag = true;
                    }
                    //��������
                    String title = JSONObject.parseObject(body).getJSONObject("trackInfo").getString("title");
                    title = DataUtil.dealString(title);
                    for (int j = 0; j < playArray.size(); j++) {
                        JSONObject palyObj = playArray.getJSONObject(j);
                        //���Ե���������Ƶ M4A_64 MP3_64 M4A_24 MP3_32
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
        //��Ҫ�ر��̷߳�����򲻻�ֹͣ
        ThreadPool.shutdown();
        while (true) {// �ȴ���������ִ�н���
            if (ThreadPool.getActiveCount() <= 0) {
                logger.info("====================�ܺ�ʱ��" + (System.currentTimeMillis() - start) / 1000 + "��=================");
                break;
            }
        }
    }

    /**
     * ���ܼ��������ݵ�����
     */
    private static void DownAlbum(Map<String, Map<String, String>> albumMap) {
        //ʹ�ö��̱߳����ļ�
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
                logger.error("�ӿڵ����ƺ��Ѿ��ﵽ����,����������!");
                return;
            }
            logger.info("ר��{}�ɹ���ȡ��{}������", albumTitle, soundMap.size());
            for (Map.Entry<String, String> sound : soundMap.entrySet()) {
                String soundName = sound.getKey();
                String soundCryptLink;
                if (info.getIsPc()) {
                    soundCryptLink = PcAesDecryptUtil.decrypt(sound.getValue(), SECRETKEY);
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
     * ��ȡר��ÿ�����ݻ�����Ϣ
     */
    private static Map<String, Map<String, String>> getAlbum(Map<String, JSONArray> map) {
        String partOfAlbum = info.getPartOfAlbum();
        int start = 0;
        int end = 0;
        if (StrUtil.isNotEmpty(partOfAlbum)) {
            String[] split1 = partOfAlbum.split("-");
            if (split1.length != 2) {
                logger.error("����partOfAlbum����");
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

                threadPool.submit(() -> {
                    int retry = 1;
                    //ʧ�����������
                    while (retry <= RETRYCOUNT) {
                        String body = HttpRequest.get(BASEURL + new Date().getTime()).
                                form(getBaseParamMap(trackId)).addHeaders(getHeaderMap(true, trackId, true)).execute().body();
                        //{"reqId":"07fbdb9b-64837146","ret":1001,"msg":"ϵͳ��æ�����Ժ�����!"}���ô����ﵽ������
                        Integer ret = JSONObject.parseObject(body).getInteger("ret");
                        if (0 != ret) {
                            logger.error("ר��'{}',����'{}'��ȡʱ�򱨴�:{},��������,��ǰ��{}������", albumTitle, trackId, JSONObject.parseObject(body).getString("msg"), retry);
                            retry++;
                            continue;
                        }
                        JSONArray playArray = JSONObject.parseObject(body).getJSONObject("trackInfo").getJSONArray("playUrlList");
                        //��������
                        String title = JSONObject.parseObject(body).getJSONObject("trackInfo").getString("title");
                        title = DataUtil.dealString(title);
                        for (int j = 0; j < playArray.size(); j++) {
                            JSONObject palyObj = playArray.getJSONObject(j);
                            //���Ե���������Ƶ M4A_64 MP3_64 M4A_24 MP3_32 window�õ���2
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
            while (true) {// �ȴ���������ִ�н���
                if (ThreadPool.getActiveCount() <= 0) {
                    logger.info("��ȡר��'{}'ÿ�����ݻ�����Ϣ���", albumTitle);
                    break;
                }
            }
            returnMap.put(albumTitle, soundMap);
        }
        return returnMap;
    }

    /**
     * ��ҳ����ר������
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
        //��ȡר�����ƺ���������
        for (String ablum : split) {
            String body = HttpRequest.get(albumUrl).form(getParamMap(ablum)).addHeaders(getHeaderMap(false, null, false)).execute().body();
            JSONObject jsonObject = JSON.parseObject(body);
            if (HttpStatus.HTTP_OK != Integer.parseInt(jsonObject.getString("ret"))) {
                logger.error("��ר��{}��ȡʱ�򱨴�{}", ablum, jsonObject.getString("msg"));
                continue;
            }
            //��ר����������
            Integer totalCount = jsonObject.getJSONObject("data").getInteger("trackTotalCount");
            String albumTitle = jsonObject.getJSONObject("data").getJSONArray("tracks")
                    .getJSONObject(0).getString("albumTitle");
            albumTitle = DataUtil.dealString(albumTitle);
            map.put(ablum, totalCount);
            chineseMap.put(ablum, albumTitle);
            logger.info("ר��<<{}>>��ѯ���,��{}������", albumTitle, totalCount);
        }

        //���ר����id��������
        /*
         * jsonarray��ʽ,�������Ҫ�Ǳ�֤˳��,�Ա���ɸѡ�Զ������ؼ���ʱ�����
         * {
         *   "id":""//��ӦtrackId
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
                    logger.error("��ר��{}��ȡʱ�򱨴�{}", ablumId, jsonObject.getString("msg"));
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
     * ����ͷ����
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
     * �������ݲ���,ͷһ�β�ѯר������(��ȡר������ʹ��)
     */
    public static Map<String, Object> getParamMap(String album) {
        Map<String, Object> map = new HashMap<>();
        map.put("albumId", album);
        map.put("pageNum", 1);
        map.put("pageSize", 30);
        return map;
    }

    /**
     * ����ҳ����������
     */
    public static Map<String, Object> getParamMap(String album, int pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("albumId", album);
        map.put("pageNum", pageNum);
        map.put("pageSize", 100);
        return map;
    }

    /**
     * ��������
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
