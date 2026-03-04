package xyz.zcraft.elect;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.List;

@Data
public class ElectRequest {
    private String studentId;
    private String courseCode;
    private String teachClassId;
    private String calendarId;
    private List<TeachClass> electClasses;
    private List<TeachClass> withdrawClasses;
    private int roundId;

    public String generateElectData() {
        JSONObject object = new JSONObject();
        object.put("roundId", roundId);
        object.put("elecClassList", electClasses);
        object.put("withdrawClassList", withdrawClasses);
        return object.toJSONString();
    }
}
