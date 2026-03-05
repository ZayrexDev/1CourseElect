package xyz.zcraft.elect;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

public record ElectResult(String status, JSONArray successCourses, JSONObject failedReasons) {
}
