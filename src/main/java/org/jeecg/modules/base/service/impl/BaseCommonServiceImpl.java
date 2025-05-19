package org.jeecg.modules.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import io.minio.GetObjectResponse;
import io.minio.StatObjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.dto.LogDTO;
import org.jeecg.common.constant.enums.ClientTerminalTypeEnum;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.*;
import org.jeecg.modules.base.mapper.BaseCommonMapper;
import org.jeecg.modules.base.service.BaseCommonService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * @Description: common实现类
 * @author: jeecg-boot
 */
@Service
@Slf4j
public class BaseCommonServiceImpl implements BaseCommonService {

    @Resource
    private BaseCommonMapper baseCommonMapper;

    @Override
    public void addLog(LogDTO logDTO) {
        if (oConvertUtils.isEmpty(logDTO.getId())) {
            logDTO.setId(String.valueOf(IdWorker.getId()));
        }
        //保存日志（异常捕获处理，防止数据太大存储失败，导致业务失败）JT-238
        try {
            logDTO.setCreateTime(new Date());
            baseCommonMapper.saveLog(logDTO);
        } catch (Exception e) {
            log.warn(" LogContent length : " + logDTO.getLogContent().length());
            log.warn(e.getMessage());
        }
    }

    @Override
    public void addLog(String logContent, Integer logType, Integer operatetype, LoginUser user) {
        LogDTO sysLog = new LogDTO();
        sysLog.setId(String.valueOf(IdWorker.getId()));
        //注解上的描述,操作日志内容
        sysLog.setLogContent(logContent);
        sysLog.setLogType(logType);
        sysLog.setOperateType(operatetype);
        try {
            //获取request
            HttpServletRequest request = SpringContextUtils.getHttpServletRequest();
            //设置IP地址
            sysLog.setIp(IpUtils.getIpAddr(request));

            try {
                //设置客户端
                if (BrowserUtils.isDesktop(request)) {
                    sysLog.setClientType(ClientTerminalTypeEnum.PC.getKey());
                } else {
                    sysLog.setClientType(ClientTerminalTypeEnum.APP.getKey());
                }
            } catch (Exception e) {
                //e.printStackTrace();
            }
        } catch (Exception e) {
            sysLog.setIp("127.0.0.1");
        }
        //获取登录用户信息
        if (user == null) {
            try {
                user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
            } catch (Exception e) {
                //e.printStackTrace();
            }
        }
        if (user != null) {
            sysLog.setUserid(user.getUsername());
            sysLog.setUsername(user.getRealname());
        }
        sysLog.setCreateTime(new Date());
        //保存日志（异常捕获处理，防止数据太大存储失败，导致业务失败）JT-238
        try {
            baseCommonMapper.saveLog(sysLog);
        } catch (Exception e) {
            log.warn(" LogContent length : " + sysLog.getLogContent().length());
            log.warn(e.getMessage());
        }
    }

    @Override
    public void addLog(String logContent, Integer logType, Integer operateType) {
        addLog(logContent, logType, operateType, null);
    }


    /**
     * 下载视频
     *
     * @param filename
     * @param request
     * @param response
     * @throws Exception
     */
    @Override
    public void downloadVideo(String filename, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (StringUtils.isNotBlank(filename)) {
            log.info("download:" + filename);
            String range = request.getHeader("Range");
            log.info("current request rang:" + range);
            //获取文件信息
            StatObjectResponse statObjectResponse = MinioUtil.getStatObjectResponse(filename);
            //开始下载位置
            long startByte = 0;
            //结束下载位置
            long endByte = statObjectResponse.size() - 1;
            log.info("文件开始位置：{}，文件结束位置：{}，文件总长度：{}", startByte, endByte, statObjectResponse.size());

            //有range的话
            if (StringUtils.isNotBlank(range) && range.contains("bytes=") && range.contains("-")) {
                range = range.substring(range.lastIndexOf("=") + 1).trim();
                String[] ranges = range.split("-");
                try {
                    //判断range的类型
                    if (ranges.length == 1) {
                        //类型一：bytes=-2343
                        if (range.startsWith("-")) {
                            endByte = Long.parseLong(ranges[0]);
                        }
                        //类型二：bytes=2343-
                        else if (range.endsWith("-")) {
                            startByte = Long.parseLong(ranges[0]);
                        }
                    }
                    //类型三：bytes=22-2343
                    else if (ranges.length == 2) {
                        startByte = Long.parseLong(ranges[0]);
                        endByte = Long.parseLong(ranges[1]);
                    }

                } catch (NumberFormatException e) {
                    startByte = 0;
                    endByte = statObjectResponse.size() - 1;
                    log.error("Range Occur Error, Message:" + e.getLocalizedMessage());
                }
            }

            //要下载的长度
            long contentLength = endByte - startByte + 1;
            //文件类型
            String contentType = request.getServletContext().getMimeType(filename);

            //解决下载文件时文件名乱码问题
            byte[] fileNameBytes = filename.getBytes(StandardCharsets.UTF_8);
            filename = new String(fileNameBytes, 0, fileNameBytes.length, StandardCharsets.ISO_8859_1);

            //各种响应头设置
            //支持断点续传，获取部分字节内容：
            response.setHeader("Accept-Ranges", "bytes");
            //http状态码要为206：表示获取部分内容
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setContentType(contentType);
            response.setHeader("Last-Modified", statObjectResponse.lastModified().toString());
            //inline表示浏览器直接使用，attachment表示下载，fileName表示下载的文件名
            response.setHeader("Content-Disposition", "inline;filename=" + filename);
            response.setHeader("Content-Length", String.valueOf(contentLength));
            //Content-Range，格式为：[要下载的开始位置]-[结束位置]/[文件总大小]
            response.setHeader("Content-Range", "bytes " + startByte + "-" + endByte + "/" + statObjectResponse.size());
            response.setHeader("ETag", "\"".concat(statObjectResponse.etag()).concat("\""));

            try {
                GetObjectResponse stream = MinioUtil.getObjectResponse(statObjectResponse, startByte, contentLength);
                BufferedOutputStream os = new BufferedOutputStream(response.getOutputStream());
                byte[] buffer = new byte[1024];
                int len;
                while ((len = stream.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
                os.flush();
                os.close();
                response.flushBuffer();
                log.info("下载完毕");
            } catch (ClientAbortException e) {
                log.warn("用户停止下载：" + startByte + "-" + endByte);
                //捕获此异常表示拥护停止下载
            } catch (IOException e) {
                e.printStackTrace();
                log.error("用户下载IO异常，Message：{}", e.getLocalizedMessage());
            }
        }
    }


}
