package xyz.zcraft.elect;

import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;

@Getter
public class User {
    private final JSONObject dataJson;
    private final String cookie;
    private final int uid;
    private final String name;
    private final int sex;
    private final String facultyName;
    private final String aesKey;
    private final String aesIv;

    public User(JSONObject dataJson, String cookie) {
        this.dataJson = dataJson;
        this.cookie = cookie;

        this.uid = dataJson.getJSONObject("user").getIntValue("uid");
        this.name = dataJson.getJSONObject("user").getString("name");
        this.sex = dataJson.getJSONObject("user").getIntValue("sex");
        this.facultyName = dataJson.getJSONObject("user").getString("facultyName");
        this.aesKey = dataJson.getString("aesKey");
        this.aesIv = dataJson.getString("aesIv");
    }

    @Override
    public String toString() {
        return "User{" +
                "uid=" + uid +
                ", name='" + name + '\'' +
                ", sex=" + sex +
                ", facultyName='" + facultyName + '\'' +
                ", aesKey='" + aesKey + '\'' +
                ", aesIv='" + aesIv + '\'' +
                '}';
    }
}
