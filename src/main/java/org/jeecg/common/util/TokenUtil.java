package org.jeecg.common.util;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.util.JwtUtil;

import javax.servlet.http.HttpServletRequest;

/**
 * token工具
 */
public class TokenUtil {

    public static final String LOGIN_TOKEN_KEY = "X-Exam-Token";


    /**
     * 获取token
     *
     * @param request
     * @return
     */
    public static String getToken(HttpServletRequest request) {
        //从header中获取token
        String token = request.getHeader(LOGIN_TOKEN_KEY);
        //如果header中不存在token，则从参数中获取token
        if (StringUtils.isBlank(token)) {
            token = request.getParameter(LOGIN_TOKEN_KEY);
        }
        //token为空
        if (StringUtils.isBlank(token)) {
            return null;
        }
        return token;
    }


    /**
     * 获取用户名
     *
     * @param token
     * @return
     */
    public static String getUserName(String token) {
        //token为空
        if (StringUtils.isBlank(token)) {
            throw new JeecgBootException("请先登录");
        }

        return JwtUtil.getUsername(token);
    }

    /**
     * 获取用户id
     *
     * @param token
     * @return
     */
    public static String getUserId(String token) {
        return JwtUtil.getFieldValue(token, "userId");
    }


}
