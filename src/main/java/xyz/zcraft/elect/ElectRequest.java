package xyz.zcraft.elect;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.List;

@Data
public class ElectRequest {
    private String studentId;
    private String courseCode;
    private long teachClassId;
    private String calendarId;
    private List<TeachClass> electClasses;
    private List<TeachClass> withdrawClasses;
    private int roundId;
    private TeachClass mainClass;

    public void setMainClass(TeachClass mainClass) {
        this.mainClass = mainClass;
        if (mainClass == null) return;
        this.teachClassId = mainClass.teachClassId();
        this.courseCode = mainClass.courseCode();
    }

    public String generateElectData() {
        JSONObject object = new JSONObject();
        object.put("roundId", roundId);
        object.put("elecClassList", electClasses);
        object.put("withdrawClassList", withdrawClasses);
        return object.toJSONString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{").append(mainClass.newTeachClassCode()).append("}-选[");
        for (TeachClass teachClass : electClasses) {
            sb.append(teachClass.newTeachClassCode()).append(teachClass.courseName()).append(",");
        }
        sb.append("],退[");
        for (TeachClass teachClass : withdrawClasses) {
            sb.append(teachClass.newTeachClassCode()).append(teachClass.courseName()).append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
