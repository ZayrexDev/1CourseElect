package xyz.zcraft.elect;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Course {
    private CourseType courseType;
    private int completeStatus;
    private CourseData courseData;
    private String tag;
}
