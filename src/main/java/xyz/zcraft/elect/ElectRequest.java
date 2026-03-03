package xyz.zcraft.elect;

public record ElectRequest(
        String studentId,
        String courseCode,
        String teachClassId,
        String calendarId,
        String currentTime,
        String electData){}
