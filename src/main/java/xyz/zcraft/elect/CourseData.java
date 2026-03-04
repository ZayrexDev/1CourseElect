package xyz.zcraft.elect;

import com.alibaba.fastjson2.JSONObject;

public record CourseData(String teachClassId,
                         String teachClassCode,
                         String newTeachClassCode,
                         String courseCode,
                         String newCourseCode,
                         String courseName,
                         String teacherName,
                         double credits,
                         String jp,
                         String campus) {
    public String toJson() {
        return JSONObject.toJSONString(this);
    }

    public static CourseData fromJson(String json) {
        return JSONObject.parseObject(json, CourseData.class);
    }
}
