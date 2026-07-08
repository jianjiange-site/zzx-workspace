package com.dating.server.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.payment.constant.Constants;
import com.dating.server.payment.entity.CoinAccount;
import com.dating.server.payment.entity.CoinLedger;
import com.dating.server.payment.exception.BizException;
import com.dating.server.payment.exception.ErrorCodes;
import com.dating.server.payment.mapper.CoinAccountMapper;
import com.dating.server.payment.mapper.CoinLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 金币服务 —— 双账户（免费 + 付费），先扣免费再扣付费
 *
 * 设计文档 §6：
 * - 查/加/扣幂等
 * - 扣减顺序：先免费，免费不够再付费；都不够抛 INSUFFICIENT
 * - 幂等键 + 部分唯一索引兜底
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoinService {

    private final CoinAccountMapper accountMapper;
    private final CoinLedgerMapper ledgerMapper;

    /** 查询金币余额 */
    public long getCoins(Long userId) {
        CoinAccount acc = accountMapper.selectById(userId);
        if (acc == null) return 0;
        return acc.getBalance() + acc.getPaidBalance();
    }

    /** 加免费金币 */
    @Transactional
    public void addCoins(Long userId, long amount, String reason) {
        CoinAccount acc = ensureAccount(userId);
        acc.setBalance(acc.getBalance() + amount);
        accountMapper.updateById(acc);

        CoinLedger ledger = new CoinLedger();
        ledger.setUserId(userId);
        ledger.setType(Constants.LEDGER_INCOME);
        ledger.setAmount(amount);
        ledger.setBalanceAfter(acc.getBalance());
        ledger.setPaidAmount(0L);
        ledger.setPaidBalanceAfter(acc.getPaidBalance());
        ledger.setReason(reason);
        ledgerMapper.insert(ledger);
    }

    /** 加付费金币（充值成功时调） */
    @Transactional
    public void addPaidCoins(Long userId, long amount, String reason) {
        CoinAccount acc = ensureAccount(userId);
        acc.setPaidBalance(acc.getPaidBalance() + amount);
        accountMapper.updateById(acc);

        CoinLedger ledger = new CoinLedger();
        ledger.setUserId(userId);
        ledger.setType(Constants.LEDGER_INCOME);
        ledger.setAmount(0L);
        ledger.setBalanceAfter(acc.getBalance());
        ledger.setPaidAmount(amount);
        ledger.setPaidBalanceAfter(acc.getPaidBalance());
        ledger.setReason(reason);
        ledgerMapper.insert(ledger);
    }

    /**
     * 扣金币（幂等）
     *
     * 先扣免费币，免费不够再扣付费币。都不够 → INSUFFICIENT。
     *
     * @param userId         用户 ID
     * @param amount         扣减数量
     * @param reason         变动原因
     * @param idempotencyKey 幂等键（可为 null，null 不走幂等）
     * @return 扣减是否成功
     */
    @Transactional
    public boolean consumeCoins(Long userId, long amount, String reason, String idempotencyKey) {
        // 幂等检查
        if (idempotencyKey != null) {
            Long existing = ledgerMapper.selectCount(new LambdaQueryWrapper<CoinLedger>()
                    .eq(CoinLedger::getUserId, userId)
                    .eq(CoinLedger::getIdempotencyKey, idempotencyKey));
            if (existing != null && existing > 0) {
                log.debug("幂等命中，跳过重复扣减: userId={}, key={}", userId, idempotencyKey);
                return true;
            }
        }

        CoinAccount acc = accountMapper.selectById(userId);
        if (acc == null) {
            throw new BizException(ErrorCodes.ACCOUNT_NOT_FOUND, "金币账户不存在: " + userId);
        }

        long freeBalance = acc.getBalance();
        long paidBalance = acc.getPaidBalance();

        if (freeBalance + paidBalance < amount) {
            log.warn("金币不足: userId={}, 余额={}, 需要={}", userId, freeBalance + paidBalance, amount);
            return false;
        }

        // 先扣免费，免费不够再扣付费
        long freeTake = Math.min(freeBalance, amount);
        long paidTake = amount - freeTake;

        acc.setBalance(freeBalance - freeTake);
        acc.setPaidBalance(paidBalance - paidTake);

        try {
            accountMapper.updateById(acc);
        } catch (Exception e) {
            // 乐观锁冲突或余额 CHECK 约束失败
            throw new BizException(ErrorCodes.OPTIMISTIC_LOCK_CONFLICT, "账户并发更新冲突");
        }

        // 写流水
        CoinLedger ledger = new CoinLedger();
        ledger.setUserId(userId);
        ledger.setType(Constants.LEDGER_EXPENSE);
        ledger.setAmount(freeTake);
        ledger.setBalanceAfter(acc.getBalance());
        ledger.setPaidAmount(paidTake);
        ledger.setPaidBalanceAfter(acc.getPaidBalance());
        ledger.setReason(reason);
        ledger.setIdempotencyKey(idempotencyKey);
        try {
            ledgerMapper.insert(ledger);
        } catch (DuplicateKeyException e) {
            // 幂等键唯一冲突：并发重复扣，反查上次结果
            log.warn("幂等键冲突，并发扣减: userId={}, key={}", userId, idempotencyKey);
            return true;
        }

        log.info("金币扣减成功: userId={}, freeTake={}, paidTake={}, reason={}",
                userId, freeTake, paidTake, reason);
        return true;
    }

    /** 确保账户存在（懒初始化） */
    private CoinAccount ensureAccount(Long userId) {
        CoinAccount acc = accountMapper.selectById(userId);
        if (acc == null) {
            acc = new CoinAccount();
            acc.setUserId(userId);
            acc.setBalance(0L);
            acc.setPaidBalance(0L);
            acc.setVersion(0);
            accountMapper.insert(acc);
        }
        return acc;
    }
}
