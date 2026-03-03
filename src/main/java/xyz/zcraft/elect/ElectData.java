package xyz.zcraft.elect;

import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.LinkedList;

@Getter @RequiredArgsConstructor
public class ElectData {
    private final int roundId;
    private final LinkedList<Course> electCourses = new LinkedList<>();
    private final LinkedList<Course> withdrawCourses = new LinkedList<>();

    public String toDataString() {
        JSONObject object = new JSONObject();
        object.put("roundId", roundId);
        object.put("elecClassList", electCourses);
        object.put("withdrawClassList", withdrawCourses);
        return object.toJSONString();
    }
}
