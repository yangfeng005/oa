package org.jeecg.common.util;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * IP地址
 *
 * @Author scott
 * @email jeecgos@163.com
 * @Date 2019年01月14日
 */
@Component
@Slf4j
public class IpLocationUtil {

    @Value("${ip.location.enable}")
    private boolean locationEnable;

    @Value("${ip.location.api}")
    private String locationApi;

    @Value("${ip.location.key}")
    private String locationKey;

    /**
     * 获取ip归属地
     *
     * @param ip
     * @return
     */
    public String getIpLocation(String ip) {
        if (!locationEnable) {
            return null;
        }
        try {
            String apiUrl = locationApi + "?ip=" + ip + "&key=" + locationKey;
            String result = HttpUtil.get(apiUrl, 3000);
            JSONObject jsonObject = JSON.parseObject(result);
            if (!Objects.isNull(jsonObject) && "200".equals(jsonObject.getString("code"))) {
                return jsonObject.getJSONObject("data").getJSONObject("location").getString("province");
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("IP数据云查询IP归属地接口调用异常：{}", e.getMessage());
        }
        return null;
    }

}
