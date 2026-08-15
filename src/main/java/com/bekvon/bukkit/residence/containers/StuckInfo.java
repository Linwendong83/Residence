package com.bekvon.bukkit.residence.containers;

public class StuckInfo {

    private int times = 0;
    private long lastDeniedMove = 0L;
    private String residenceName;

    public StuckInfo() {
    }

    public int getTimesDenied() {
        return times;
    }

    public int registerDeniedMove(String residenceName, long deniedAt, long resetAfter) {
        boolean sameResidence = this.residenceName == null
                ? residenceName == null
                : this.residenceName.equals(residenceName);
        long elapsed = deniedAt - lastDeniedMove;

        if (!sameResidence || elapsed < 0L || elapsed > resetAfter)
            times = 0;

        times++;
        this.residenceName = residenceName;
        lastDeniedMove = deniedAt;
        return times;
    }

    public void reset() {
        times = 0;
        lastDeniedMove = 0L;
        residenceName = null;
    }

}
