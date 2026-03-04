package xyz.zcraft.util;

import xyz.zcraft.elect.ElectRequest;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class ElectRequestBodyBuilder {
    private final String processedKey;
    private final String processedIv;
    private final long overrideTime;

    public ElectRequestBodyBuilder(String aesKey, String aesIv, long overrideTime) {
        processedKey = paramHandler(aesKey);
        processedIv = paramHandler(aesIv);
        this.overrideTime = overrideTime;
    }

    public ElectRequestBodyBuilder(String aesKey, String aesIv) {
        processedKey = paramHandler(aesKey);
        processedIv = paramHandler(aesIv);
        this.overrideTime = System.currentTimeMillis();
    }

    public String buildRequestBody(ElectRequest request) throws Exception {
        String cipherBody = getCipherBody(request);
        String checkCode = getCheckCode(request);
        return "{\"ciphertext\":\"" + cipherBody + "\",\"checkCode\":\"" + checkCode + "\"}";
    }

    private String getCipherBody(ElectRequest request) throws Exception {
        String plainText = request.getStudentId() +
                "&" + request.getCourseCode() +
                "&" + request.getTeachClassId() +
                "&" + request.getCalendarId() +
                "&" + overrideTime +
                "&" + request.generateElectData();
        String urlEncodedText = encodeURIComponent(plainText);

        SecretKeySpec keySpec = new SecretKeySpec(processedKey.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(processedIv.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encryptedBytes = cipher.doFinal(urlEncodedText.getBytes(StandardCharsets.UTF_8));
        String base64CipherText = Base64.getEncoder().encodeToString(encryptedBytes);
        return encodeURIComponent(base64CipherText);
    }

    private String getCheckCode(ElectRequest request) throws Exception {
        String plainText = request.getStudentId() +
                "+" + request.getCourseCode() +
                "+" + request.getTeachClassId() +
                "+" + request.getCalendarId() +
                "+" + overrideTime +
                "+" + request.generateElectData();
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] digest = md5.digest(plainText.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static String paramHandler(String input) {
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i += 2) {
            if (i + 1 < chars.length) {
                char temp = chars[i];
                chars[i] = chars[i + 1];
                chars[i + 1] = temp;
            }
        }
        return new String(chars);
    }

    private static String encodeURIComponent(String s) {
        try {
            final String s1 = URLEncoder.encode(s, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20")
                    .replaceAll("%21", "!")
                    .replaceAll("%27", "'")
                    .replaceAll("%28", "(")
                    .replaceAll("%29", ")")
                    .replaceAll("%7E", "~");
            System.out.println(s1);
            return s1;
        } catch (Exception e) {
            throw new RuntimeException("Error encoding string", e);
        }
    }
}

