package xyz.zcraft.elect;

public record TeachClass(
        long teachClassId,
        String teachClassCode,
        String newTeachClassCode,
        String courseCode,
        String newCourseCode,
        String courseName,
        String teacherName,
        String remark,
        int currentNumber,
        int maxNumber) {

    @Override
    public String toString() {
        return newTeachClassCode + " - " + courseName + " - " + teacherName + " (" + currentNumber + "/" + maxNumber + ")" + " - " + remark;
    }
}
