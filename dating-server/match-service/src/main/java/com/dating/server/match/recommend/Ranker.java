package com.dating.server.match.recommend;

import com.dating.proto.user.DhCandidate;
import com.dating.proto.user.NearbyUser;
import com.dating.server.match.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * D1 打分排序（PRD 4.2.3）
 *
 * S(c) = base_score(c) + mutual_like_bonus(c) + new_bh_bonus(c)
 *
 * base_score(c) =
 *    0.45 * preference_similarity(c, pref)
 *    0.30 * normalize(c.beauty_score)
 *    0.15 * distance_decay(c)
 *    0.10 * activity_score(c)
 *
 * 所有权重走 Nacos 配置，运营可调。
 */
@Slf4j
@Component
public class Ranker {

    // ── BH 权重（Nacos 可覆盖） ──
    @Value("${match.score.bh_weights.pref_sim:0.45}")
    private double wPrefSim;
    @Value("${match.score.bh_weights.beauty:0.30}")
    private double wBeauty;
    @Value("${match.score.bh_weights.distance:0.15}")
    private double wDistance;
    @Value("${match.score.bh_weights.activity:0.10}")
    private double wActivity;

    // ── DH 权重（Nacos 可覆盖） ──
    @Value("${match.score.dh_weights.pref_sim:0.45}")
    private double dhWPrefSim;
    @Value("${match.score.dh_weights.beauty:0.30}")
    private double dhWBeauty;
    @Value("${match.score.dh_weights.activity:0.10}")
    private double dhWActivity;
    // DH distance 固定 0.5（无距离数据）

    // ── Bonus ──
    @Value("${match.score.mutual_like_bonus:0.20}")
    private double mutualLikeBonus;
    @Value("${match.score.new_bh_bonus:0.20}")
    private double newBhBonus;
    @Value("${match.score.new_bh_window_days:3}")
    private int newBhWindowDays;

    /** 格式化对 <low, high> 用于互检 */
    static String pairKey(long a, long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    /**
     * 对 DH 候选打分并排序，取 top N
     */
    public List<ScoredDh> rankDh(List<DhCandidate> candidates, PreferenceProfile pref,
                                  int userAge, int userBeauty) {
        return candidates.stream()
                .map(c -> new ScoredDh(c, scoreDh(c, pref, userAge, userBeauty)))
                .sorted((a, b) -> -Double.compare(a.score, b.score))
                .limit(CandidateRecaller.POOL_SIZE)
                .collect(Collectors.toList());
    }

    /**
     * 对 BH 候选打分并排序，取 top N
     */
    public List<ScoredBh> rankBh(List<NearbyUser> candidates, PreferenceProfile pref,
                                  int userAge, int userBeauty,
                                  Set<Long> mySwipedTargets, // 反查 "对方是否右划过我"
                                  Map<String, Boolean> mutualCache) {
        long nowMs = Instant.now().toEpochMilli();
        long newBhThresholdMs = (long) newBhWindowDays * 86400 * 1000L;

        return candidates.stream()
                .map(c -> new ScoredBh(c, scoreBh(c, pref, userAge, userBeauty,
                        nowMs, newBhThresholdMs, mutualCache)))
                .sorted((a, b) -> -Double.compare(a.score, b.score))
                .limit(CandidateRecaller.POOL_SIZE)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════
    //  DH 打分
    // ═══════════════════════════

    private double scoreDh(DhCandidate c, PreferenceProfile pref, int userAge, int userBeauty) {
        double prefSim = preferenceSimilarity(c.getAge(), c.getBeautyScore(), c.getRace(), pref);
        double beautyNorm = normalizeBeauty(c.getBeautyScore());
        double activity = 0.5; // DH 固定 0.5（无活跃度数据）

        return dhWPrefSim * prefSim
             + dhWBeauty * beautyNorm
             + 0.5 * 0.15 // DH distance 固定 0.5 × 权重 0.15（doc: distance_decay=0.5 for DH）
             + dhWActivity * activity;
    }

    // ═══════════════════════════
    //  BH 打分
    // ═══════════════════════════

    private double scoreBh(NearbyUser c, PreferenceProfile pref, int userAge, int userBeauty,
                            long nowMs, long newBhThresholdMs,
                            Map<String, Boolean> mutualCache) {
        double prefSim = preferenceSimilarity(c.getAge(), c.getBeautyScore(), c.getRace(), pref);
        double beautyNorm = normalizeBeauty(c.getBeautyScore());
        double distance = distanceDecay(c.getDistanceKm());
        double activity = activityScore(nowMs, c.getLastActiveAt());

        double base = wPrefSim * prefSim + wBeauty * beautyNorm
                    + wDistance * distance + wActivity * activity;

        // Bonus
        double bonus = 0;
        // mutual_like_bonus: 对方右划过当前用户
        Boolean mutual = mutualCache.get(String.valueOf(c.getUserId()));
        if (Boolean.TRUE.equals(mutual)) {
            bonus += mutualLikeBonus;
        }
        // new_bh_bonus: 3 天内注册的新 BH
        if ((nowMs - c.getCreatedAt()) <= newBhThresholdMs) {
            bonus += newBhBonus;
        }

        return base + bonus;
    }

    // ═══════════════════════════
    //  子评分
    // ═══════════════════════════

    /** 偏好相似度：age 高斯 PDF × beauty 高斯 PDF × race_dist */
    double preferenceSimilarity(int age, int beauty, String race, PreferenceProfile pref) {
        double ageSim = gaussianPdf(age, pref.ageMean(), pref.ageStd());
        double beautySim = gaussianPdf(beauty, pref.beautyMean(), pref.beautyStd());
        double raceSim = pref.raceDist().getOrDefault(race != null ? race : "", 0.0);
        if (raceSim == 0 && !pref.raceDist().isEmpty()) {
            raceSim = 0.01; // 极小值避免乘积为 0
        }
        if (pref.raceDist().isEmpty()) raceSim = 1.0; // 无偏好时不惩罚
        return ageSim * beautySim * raceSim;
    }

    /** 高斯 PDF（归一化到 [0,1]） */
    private double gaussianPdf(double x, double mean, double std) {
        if (std <= 0) return 1.0;
        double z = (x - mean) / std;
        return Math.exp(-0.5 * z * z);
    }

    /** 颜值 0-1 归一化（假设颜值 0-1000） */
    private double normalizeBeauty(int beauty) {
        return Math.min(1.0, Math.max(0, beauty / 1000.0));
    }

    /** 距离衰减 exp(-d/50km) */
    private double distanceDecay(double distanceKm) {
        if (distanceKm <= 0) return 0.5; // DH 无距离数据
        return Math.exp(-distanceKm / 50.0);
    }

    /** 活跃度分数 exp(-天数/7) */
    private double activityScore(long nowMs, long lastActiveAtMs) {
        if (lastActiveAtMs <= 0) return 0.3; // 无数据，中性偏低
        double daysSinceActive = (nowMs - lastActiveAtMs) / 86400_000.0;
        return Math.exp(-daysSinceActive / 7.0);
    }

    // ═══════════════════════════
    //  分值记录
    // ═══════════════════════════

    public record ScoredDh(DhCandidate candidate, double score) {}
    public record ScoredBh(NearbyUser candidate, double score) {}
}
