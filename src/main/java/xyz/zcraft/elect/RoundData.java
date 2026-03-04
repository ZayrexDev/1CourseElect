package xyz.zcraft.elect;

public record RoundData(int id,
                        int calendarId,
                        int turn,
                        String name,
                        int openFlag,
                        String beginTime,
                        String endTime,
                        String calendarName,
                        String remark) {

    @Override
    public String toString() {
        return id + "-"  + turn + "-" + calendarName + "-" + name;
    }
}
