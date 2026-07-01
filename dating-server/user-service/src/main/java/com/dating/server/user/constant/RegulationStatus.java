package com.dating.server.user.constant;

/**
 * 监管状态枚举
 * DB SMALLINT 与 proto 取值不一致，差 1：
 *   DB:   0=正常  2=封禁     5=暂停
 *   Proto:         BANNED=3  SUSPENDED=6
 * 封禁判定固定 IN (2, 5)，Proto 映射在 RegulationStatusMapping 中
 * 设计文档 §5.7
 */
public enum RegulationStatus {

    NORMAL(0),
    BANNED(2),
    SUSPENDED(5);

    private final int dbValue;

    RegulationStatus(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }

    /** 从 DB 值转枚举，默认 NORMAL */
    public static RegulationStatus fromDbValue(Integer dbValue) {
        if (dbValue == null) return NORMAL;
        for (RegulationStatus r : values()) {
            if (r.dbValue == dbValue) return r;
        }
        return NORMAL;
    }

    /** 快捷判断是否被封禁或暂停 */
    public static boolean isBannedOrSuspended(Integer dbValue) {
        return dbValue != null && (dbValue == BANNED.dbValue || dbValue == SUSPENDED.dbValue);
    }
}
