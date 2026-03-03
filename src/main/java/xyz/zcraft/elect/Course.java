package xyz.zcraft.elect;

import com.alibaba.fastjson2.JSONObject;

public record Course(String teachClassId,
                     String teachClassCode,
                     String newTeachClassCode,
                     String courseCode,
                     String newCourseCode,
                     String courseName,
                     String teacherName) {
    public String toJson() {
        return JSONObject.toJSONString(this);
    }

    public static Course fromJson(String json) {
        return JSONObject.parseObject(json, Course.class);
    }
}
