package com.example.Contract_review.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * 火山引擎API签名工具类
 *
 * 用于生成火山引擎（字节跳动）API请求的签名
 */
public class VolcEngineSignature {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String SERVICE = "ml_maas";
    private static final String REGION = "cn-beijing";

    /**
     * 生成火山引擎API签名
     *
     * @param accessKey 访问密钥
     * @param secretKey 秘密密钥
     * @param method HTTP方法
     * @param uri 请求URI
     * @param queryString 查询字符串
     * @param headers 请求头
     * @param payload 请求体
     * @param timestamp 时间戳
     * @return Authorization头值
     */
    public static String generateSignature(String accessKey, String secretKey, String method,
                                         String uri, String queryString, String headers,
                                         String payload, Date timestamp) {
        try {
            // 1. 创建规范请求
            String canonicalRequest = createCanonicalRequest(method, uri, queryString, headers, payload);

            // 2. 创建待签名字符串
            String stringToSign = createStringToSign(canonicalRequest, timestamp);

            // 3. 计算签名
            String signature = calculateSignature(secretKey, stringToSign, timestamp);

            // 4. 构建Authorization头
            return buildAuthorizationHeader(accessKey, signature, timestamp);

        } catch (Exception e) {
            throw new RuntimeException("生成签名失败", e);
        }
    }

    /**
     * 创建规范请求
     */
    private static String createCanonicalRequest(String method, String uri, String queryString,
                                               String headers, String payload) throws Exception {
        StringBuilder canonicalRequest = new StringBuilder();

        canonicalRequest.append(method).append("\n");
        canonicalRequest.append(uri).append("\n");
        canonicalRequest.append(queryString != null ? queryString : "").append("\n");
        canonicalRequest.append(headers).append("\n");
        canonicalRequest.append("\n");
        canonicalRequest.append("host\n");
        canonicalRequest.append(sha256Hex(payload));

        return canonicalRequest.toString();
    }

    /**
     * 创建待签名字符串
     */
    private static String createStringToSign(String canonicalRequest, Date timestamp) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");

        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        timestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        String date = dateFormat.format(timestamp);
        String timestampStr = timestampFormat.format(timestamp);

        String credentialScope = date + "/" + REGION + "/" + SERVICE + "/request";

        StringBuilder stringToSign = new StringBuilder();
        stringToSign.append(ALGORITHM).append("\n");
        stringToSign.append(timestampStr).append("\n");
        stringToSign.append(credentialScope).append("\n");
        stringToSign.append(sha256Hex(canonicalRequest));

        return stringToSign.toString();
    }

    /**
     * 计算签名
     */
    private static String calculateSignature(String secretKey, String stringToSign, Date timestamp) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = dateFormat.format(timestamp);

        byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, date);
        byte[] kRegion = hmacSha256(kDate, REGION);
        byte[] kService = hmacSha256(kRegion, SERVICE);
        byte[] kSigning = hmacSha256(kService, "request");

        byte[] signature = hmacSha256(kSigning, stringToSign);
        return bytesToHex(signature);
    }

    /**
     * 构建Authorization头
     */
    private static String buildAuthorizationHeader(String accessKey, String signature, Date timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = dateFormat.format(timestamp);

        String credentialScope = date + "/" + REGION + "/" + SERVICE + "/request";
        String credential = accessKey + "/" + credentialScope;

        return ALGORITHM + " Credential=" + credential + ", SignedHeaders=host, Signature=" + signature;
    }

    /**
     * SHA256哈希
     */
    private static String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    /**
     * HMAC-SHA256
     */
    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(keySpec);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}