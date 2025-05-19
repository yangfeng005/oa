package org.jeecg.common.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IoUtil;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.util.filter.SsrfFileTypeFilter;
import org.jeecg.common.util.filter.StrAttackFilter;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLDecoder;
import java.util.Objects;

/**
 * minio文件上传工具类
 *
 * @author: jeecg-boot
 */
@Slf4j
public class MinioUtil {
    private static String minioUrl;
    private static String minioName;
    private static String minioPass;
    private static String bucketName;

    public static void setMinioUrl(String minioUrl) {
        MinioUtil.minioUrl = minioUrl;
    }

    public static void setMinioName(String minioName) {
        MinioUtil.minioName = minioName;
    }

    public static void setMinioPass(String minioPass) {
        MinioUtil.minioPass = minioPass;
    }

    public static void setBucketName(String bucketName) {
        MinioUtil.bucketName = bucketName;
    }

    public static String getMinioUrl() {
        return minioUrl;
    }

    public static String getBucketName() {
        return bucketName;
    }

    private static MinioClient minioClient = null;

    /**
     * 上传文件
     *
     * @param file
     * @return
     */
    public static String upload(MultipartFile file, String bizPath, String customBucket) throws Exception {
        String fileUrl = "";
        //update-begin-author:wangshuai date:20201012 for: 过滤上传文件夹名特殊字符，防止攻击
        bizPath = StrAttackFilter.filter(bizPath);
        //update-end-author:wangshuai date:20201012 for: 过滤上传文件夹名特殊字符，防止攻击

        //update-begin-author:liusq date:20210809 for: 过滤上传文件类型
        SsrfFileTypeFilter.checkUploadFileType(file);
        //update-end-author:liusq date:20210809 for: 过滤上传文件类型

        String newBucket = bucketName;
        if (oConvertUtils.isNotEmpty(customBucket)) {
            newBucket = customBucket;
        }
        try {
            initMinio(minioUrl, minioName, minioPass);
            // 检查存储桶是否已经存在
            if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(newBucket).build())) {
                log.info("Bucket already exists.");
            } else {
                // 创建一个名为ota的存储桶
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(newBucket).build());
                log.info("create a new bucket.");
            }
            InputStream stream = file.getInputStream();
            // 获取文件名
            String orgName = file.getOriginalFilename();
            if ("".equals(orgName)) {
                orgName = file.getName();
            }
            orgName = CommonUtils.getFileName(orgName);
            String objectName = bizPath + "/"
                    + (orgName.indexOf(".") == -1
                    ? orgName + "_" + System.currentTimeMillis()
                    : orgName.substring(0, orgName.lastIndexOf(".")) + "_" + System.currentTimeMillis() + orgName.substring(orgName.lastIndexOf("."))
            );

            // 使用putObject上传一个本地文件到存储桶中。
            if (objectName.startsWith(SymbolConstant.SINGLE_SLASH)) {
                objectName = objectName.substring(1);
            }
            PutObjectArgs objectArgs = PutObjectArgs.builder().object(objectName)
                    .bucket(newBucket)
                    .contentType("application/octet-stream")
                    .stream(stream, stream.available(), -1).build();
            minioClient.putObject(objectArgs);
            stream.close();
            fileUrl = minioUrl + newBucket + "/" + objectName;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return fileUrl;
    }

    /**
     * 文件上传
     *
     * @param file
     * @param bizPath
     * @return
     */
    public static String upload(MultipartFile file, String bizPath) throws Exception {
        return upload(file, bizPath, true);
    }

    /**
     * 上传文件
     *
     * @param file
     * @param bizPath
     * @param isTimeDivision 是否按时间区分
     * @return
     */
    public static String upload(MultipartFile file, String bizPath, boolean isTimeDivision) throws Exception {
        String newBucket = bucketName;
        InputStream stream = null;
        try {
            initMinio(minioUrl, minioName, minioPass);
            // 检查存储桶是否已经存在
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(newBucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(newBucket).build());
            }
            stream = file.getInputStream();
            // 获取文件名
            String fileName = file.getOriginalFilename();
            StringBuffer objectName = new StringBuffer();
            if (isTimeDivision) {
                objectName.append(DateUtil.thisYear()).append("/").append(DateUtil.thisMonth() + 1).append("/");
            }
            if (StringUtils.isNotEmpty(bizPath)) {
                objectName.append(bizPath).append("/");
            }
            if (fileName.lastIndexOf(".") < 0) {
                fileName = fileName + ".jpg";
            }
            fileName = fileName.substring(0, fileName.lastIndexOf(".")) + "_" + System.currentTimeMillis() + fileName.substring(fileName.lastIndexOf("."));
            objectName.append(fileName);
            PutObjectArgs objectArgs = PutObjectArgs.builder().object(objectName.toString())
                    .bucket(newBucket)
                    .contentType("application/octet-stream")
                    .stream(stream, stream.available(), -1).build();
            minioClient.putObject(objectArgs);
            return objectName.toString();
        } finally {
            if (!Objects.isNull(stream)) {
                stream.close();
            }
        }
    }


    /**
     * 获取文件流
     *
     * @param bucketName
     * @param objectName
     * @return
     */
    public static InputStream getMinioFile(String bucketName, String objectName) {
        InputStream inputStream = null;
        try {
            initMinio(minioUrl, minioName, minioPass);
            GetObjectArgs objectArgs = GetObjectArgs.builder().object(objectName)
                    .bucket(bucketName).build();
            inputStream = minioClient.getObject(objectArgs);
        } catch (Exception e) {
            log.info("文件获取失败" + e.getMessage());
        }
        return inputStream;
    }

    /**
     * 获取文件流
     *
     * @param objectName
     * @return
     */
    public static InputStream getMinioFile(String objectName) {
        InputStream inputStream = null;
        try {
            initMinio(minioUrl, minioName, minioPass);
            GetObjectArgs objectArgs = GetObjectArgs.builder().object(objectName)
                    .bucket(bucketName).build();
            inputStream = minioClient.getObject(objectArgs);
        } catch (Exception e) {
            log.info("文件获取失败" + e.getMessage());
        }
        return inputStream;
    }

    /**
     * 获取文件大小
     *
     * @param objectName
     * @return
     */
    public static long getFileSize(String objectName) {
        try {
            initMinio(minioUrl, minioName, minioPass);
            StatObjectArgs objectArgs = StatObjectArgs.builder().object(objectName)
                    .bucket(bucketName).build();
            StatObjectResponse objectStat = minioClient.statObject(objectArgs);
            return objectStat.size();
        } catch (Exception e) {
            log.info("文件获取失败" + e.getMessage());
        }
        return 0;
    }

    /**
     * 删除文件
     *
     * @param bucketName
     * @param objectName
     * @throws Exception
     */
    public static void removeObject(String bucketName, String objectName) {
        try {
            initMinio(minioUrl, minioName, minioPass);
            RemoveObjectArgs objectArgs = RemoveObjectArgs.builder().object(objectName)
                    .bucket(bucketName).build();
            minioClient.removeObject(objectArgs);
        } catch (Exception e) {
            log.info("文件删除失败" + e.getMessage());
        }
    }

    /**
     * 获取文件外链
     *
     * @param bucketName
     * @param objectName
     * @param expires
     * @return
     */
    public static String getObjectUrl(String bucketName, String objectName, Integer expires) {
        initMinio(minioUrl, minioName, minioPass);
        try {
            //update-begin---author:liusq  Date:20220121  for：获取文件外链报错提示method不能为空，导致文件下载和预览失败----
            GetPresignedObjectUrlArgs objectArgs = GetPresignedObjectUrlArgs.builder().object(objectName)
                    .bucket(bucketName)
                    .expiry(expires).method(Method.GET).build();
            //update-begin---author:liusq  Date:20220121  for：获取文件外链报错提示method不能为空，导致文件下载和预览失败----
            String url = minioClient.getPresignedObjectUrl(objectArgs);
            return URLDecoder.decode(url, "UTF-8");
        } catch (Exception e) {
            log.info("文件路径获取失败" + e.getMessage());
        }
        return null;
    }

    /**
     * 初始化客户端
     *
     * @param minioUrl
     * @param minioName
     * @param minioPass
     * @return
     */
    private static MinioClient initMinio(String minioUrl, String minioName, String minioPass) {
        if (minioClient == null) {
            try {
                minioClient = MinioClient.builder()
                        .endpoint(minioUrl)
                        .credentials(minioName, minioPass)
                        .build();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return minioClient;
    }

    /**
     * 上传文件到minio
     *
     * @param stream
     * @param relativePath
     * @return
     */
    public static String upload(InputStream stream, String relativePath) throws Exception {
        initMinio(minioUrl, minioName, minioPass);
        if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            log.info("Bucket already exists.");
        } else {
            // 创建一个名为ota的存储桶
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("create a new bucket.");
        }
        PutObjectArgs objectArgs = PutObjectArgs.builder().object(relativePath)
                .bucket(bucketName)
                .contentType("application/octet-stream")
                .stream(stream, stream.available(), -1).build();
        minioClient.putObject(objectArgs);
        stream.close();
        return minioUrl + bucketName + "/" + relativePath;
    }

    /**
     * 上传文件到minio
     *
     * @param stream
     * @return
     */
    public static String uploadFile(InputStream stream, String fileName) throws Exception {
        try {
            if (Objects.isNull(stream)) {
                return null;
            }
            initMinio(minioUrl, minioName, minioPass);
            if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                log.info("Bucket already exists.");
            } else {
                // 创建一个名为ota的存储桶
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("create a new bucket.");
            }
            StringBuilder objectName = new StringBuilder();
            objectName.append(DateUtil.thisYear()).append("/").append(DateUtil.thisMonth() + 1).append("/");
            objectName.append(fileName);
            PutObjectArgs objectArgs = PutObjectArgs.builder().object(objectName.toString())
                    .bucket(bucketName)
                    .contentType("application/octet-stream")
                    .stream(stream, stream.available(), -1).build();
            minioClient.putObject(objectArgs);
            return objectName.toString();
        } finally {
            stream.close();
        }
    }

    public static StatObjectResponse getStatObjectResponse(String fileName) throws Exception {
        initMinio(minioUrl, minioName, minioPass);
        return minioClient.statObject(
                StatObjectArgs.builder().bucket(bucketName).object(fileName).build());
    }

    public static GetObjectResponse getObjectResponse(StatObjectResponse statObjectResponse,
                                                      Long startByte, Long contentLength) throws Exception {
        initMinio(minioUrl, minioName, minioPass);
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(statObjectResponse.object())
                        .offset(startByte)
                        .length(contentLength)
                        .build());
    }

    /**
     * 获取文件内容
     *
     * @param objectName
     * @return
     */
    public static String readFileContent(String objectName) {
        InputStream inputStream = null;
        try {
            initMinio(minioUrl, minioName, minioPass);
            GetObjectArgs objectArgs = GetObjectArgs.builder().object(objectName)
                    .bucket(bucketName).build();
            inputStream = minioClient.getObject(objectArgs);
            return IoUtil.readUtf8(inputStream);
        } catch (Exception e) {
            log.info("文件获取内容失败" + e.getMessage());
        }
        return null;
    }

}
