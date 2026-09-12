package com.bufferblock.schedule;

import com.bufferblock.service.BorrowReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 借用预约到点未取提醒：每分钟扫描一次超过约定取用时间（含宽限期）仍未取走的预约，
 * 置为“逾时未取”并把提醒落库。页面关闭也不影响——状态与提醒由服务端定时持久化，
 * 重新打开页面即可看到。
 *
 * <p>测试环境通过 app.borrow.overdue-scheduler.enabled=false 关闭后台扫描，
 * 逾期提醒改由测试显式调用 {@code scanOverduePickups()}，保证结果确定。</p>
 */
@Component
@ConditionalOnProperty(value = "app.borrow.overdue-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class BorrowOverdueScheduler {

    private static final Logger log = LoggerFactory.getLogger(BorrowOverdueScheduler.class);

    private final BorrowReservationService borrowReservationService;

    public BorrowOverdueScheduler(BorrowReservationService borrowReservationService) {
        this.borrowReservationService = borrowReservationService;
    }

    /** 启动后 30 秒开始首轮扫描，之后每 60 秒一轮 */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void scanOverduePickups() {
        try {
            int reminded = borrowReservationService.scanOverduePickups();
            if (reminded > 0) {
                log.info("[借用提醒] 本轮新登记 {} 张逾时未取预约", reminded);
            }
        } catch (Exception e) {
            // 单轮失败不影响下一轮调度
            log.error("[借用提醒] 逾时未取扫描失败", e);
        }
    }
}
