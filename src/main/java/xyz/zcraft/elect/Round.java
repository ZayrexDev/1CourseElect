package xyz.zcraft.elect;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data @AllArgsConstructor
public class Round {
    private RoundData roundData;
    private List<Course> courseList;
}
