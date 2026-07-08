package com.dating.server.match.recommend;

import java.util.Map;

/**
 * 用户右划偏好画像（PRD 4.2.1）
 *
 * 聚合最近 30 天右划记录的 target 用户属性分布。
 * 样本数 < 10 时回退用 D0 prior（用户自身画像）。
 */
public record PreferenceProfile(
        double ageMean,
        double ageStd,
        double beautyMean,
        double beautyStd,
        Map<String, Double> raceDist,   // {Asian: 0.4, White: 0.5, ...}
        double dhBhRatio,               // right-swiped DH / total
        int sampleCount
) {
    public boolean hasSufficientSamples() {
        return sampleCount >= 10;
    }
}
