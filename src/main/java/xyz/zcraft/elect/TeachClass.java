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
        String campusI18n,
        int currentNumber,
        int maxNumber,
        int thirdWithdrawNumber) {

    @Override
    public String toString() {
        return "%s-%s-%s(%d/%d退%d)-%s-%s".formatted(
                newTeachClassCode, courseName, teacherName,
                currentNumber, maxNumber, thirdWithdrawNumber,
                campusI18n, (remark == null || remark.isEmpty()) ? "无备注" : remark
        );
    }
}
