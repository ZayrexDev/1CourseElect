package xyz.zcraft.elect;

import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.LinkedList;

@Getter @RequiredArgsConstructor
public class ElectData {
    private final int roundId;
    private final LinkedList<CourseData> electCourses = new LinkedList<>();
    private final LinkedList<CourseData> withdrawCourses = new LinkedList<>();

    public String toDataString() {
        JSONObject object = new JSONObject();
        object.put("roundId", roundId);
        object.put("elecClassList", electCourses);
        object.put("withdrawClassList", withdrawCourses);
        return object.toJSONString();
    }
}
