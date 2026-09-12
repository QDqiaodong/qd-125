package com.bufferblock.service;

import com.bufferblock.dto.TransferQueryDTO;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.repository.BlockTransferRepository;
import com.bufferblock.repository.BufferBlockRepository;
import com.bufferblock.repository.ProductionLineRepository;
import com.bufferblock.repository.TransferFlowRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 移交确认列表：等待时长排序（最长/最短优先）、积压标记与筛选结果导出。
 */
@SpringBootTest
class BlockTransferServiceWaitSortTest {

    @Autowired
    private BlockTransferService transferService;
    @Autowired
    private BlockTransferRepository transferRepository;
    @Autowired
    private BufferBlockRepository blockRepository;
    @Autowired
    private ProductionLineRepository lineRepository;
    @Autowired
    private TransferFlowRecordRepository flowRecordRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ProductionLine fromLine;
    private ProductionLine toLine;
    private BufferBlock block;

    @BeforeEach
    void setUp() {
        flowRecordRepository.deleteAll();
        transferRepository.deleteAll();
        blockRepository.deleteAll();
        lineRepository.deleteAll();

        fromLine = saveLine("WS-FROM", "移出产线");
        toLine = saveLine("WS-TO", "移入产线");

        block = new BufferBlock();
        block.setBlockCode("BLK-WS-001");
        block.setAdapterModel("测试输送机");
        block.setThickness(BigDecimal.TEN);
        block = blockRepository.save(block);
    }

    @Test
    void longestFirstSortsPendingByWaitingDurationDesc() {
        BlockTransfer oldest = savePending("TRF-WS-001", hoursAgo(3 * 24));
        BlockTransfer middle = savePending("TRF-WS-002", hoursAgo(26));
        BlockTransfer newest = savePending("TRF-WS-003", hoursAgo(2));

        TransferQueryDTO query = pendingQuery(BlockTransferService.WAIT_SORT_LONGEST_FIRST);
        Page<BlockTransfer> page = transferService.queryTransfers(query);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(ids(page.getContent())).containsExactly(oldest.getId(), middle.getId(), newest.getId());

        // 分页同样按等待时长全局排序：第一页是等待最久的两单
        query.setSize(2);
        Page<BlockTransfer> firstPage = transferService.queryTransfers(query);
        assertThat(ids(firstPage.getContent())).containsExactly(oldest.getId(), middle.getId());
    }

    @Test
    void shortestFirstSortsPendingByWaitingDurationAsc() {
        BlockTransfer oldest = savePending("TRF-WS-011", hoursAgo(3 * 24));
        BlockTransfer middle = savePending("TRF-WS-012", hoursAgo(26));
        BlockTransfer newest = savePending("TRF-WS-013", hoursAgo(2));

        Page<BlockTransfer> page = transferService.queryTransfers(
                pendingQuery(BlockTransferService.WAIT_SORT_SHORTEST_FIRST));

        assertThat(ids(page.getContent())).containsExactly(newest.getId(), middle.getId(), oldest.getId());
    }

    @Test
    void handledTransfersSortByFrozenDurationNotByCreateTime() {
        // 登记很早但很快办结：定格等待 1 天
        BlockTransfer quickHandled = saveHandled("TRF-WS-021", hoursAgo(10 * 24), hoursAgo(9 * 24));
        // 登记较晚但压了约 3 天才办结：定格等待约 71 小时
        BlockTransfer slowHandled = saveHandled("TRF-WS-022", hoursAgo(3 * 24), hoursAgo(1));
        // 待确认：实时等待约 5 小时
        BlockTransfer pending = savePending("TRF-WS-023", hoursAgo(5));

        TransferQueryDTO query = allStatusQuery(BlockTransferService.WAIT_SORT_LONGEST_FIRST);
        Page<BlockTransfer> longest = transferService.queryTransfers(query);
        assertThat(ids(longest.getContent()))
                .containsExactly(slowHandled.getId(), quickHandled.getId(), pending.getId());

        Page<BlockTransfer> shortest = transferService.queryTransfers(
                allStatusQuery(BlockTransferService.WAIT_SORT_SHORTEST_FIRST));
        assertThat(ids(shortest.getContent()))
                .containsExactly(pending.getId(), quickHandled.getId(), slowHandled.getId());
    }

    @Test
    void longWaitingFlagOnlyMarksPendingOverThreshold() {
        BlockTransfer overThreshold = savePending("TRF-WS-031", hoursAgo(25));
        BlockTransfer underThreshold = savePending("TRF-WS-032", hoursAgo(1));
        // 已确认单即使定格等待超过 24 小时也不标记积压
        BlockTransfer handled = saveHandled("TRF-WS-033", hoursAgo(40), hoursAgo(10));

        TransferQueryDTO query = allStatusQuery(BlockTransferService.WAIT_SORT_LONGEST_FIRST);
        Page<BlockTransfer> page = transferService.queryTransfers(query);

        assertThat(flagOf(page.getContent(), overThreshold.getId())).isTrue();
        assertThat(flagOf(page.getContent(), underThreshold.getId())).isFalse();
        assertThat(flagOf(page.getContent(), handled.getId())).isFalse();
    }

    @Test
    void listForExportReturnsAllFilteredRowsInListOrder() {
        // 12 单超过默认页大小 10，验证导出不分页且顺序与列表一致
        List<BlockTransfer> transfers = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            transfers.add(savePending("TRF-WS-1" + String.format("%02d", i), hoursAgo(i * 3)));
        }

        TransferQueryDTO query = pendingQuery(BlockTransferService.WAIT_SORT_LONGEST_FIRST);
        List<BlockTransfer> exported = transferService.listForExport(query);

        assertThat(exported).hasSize(12);
        List<Long> pagedOrder = new ArrayList<>();
        query.setPage(1);
        pagedOrder.addAll(ids(transferService.queryTransfers(query).getContent()));
        query.setPage(2);
        pagedOrder.addAll(ids(transferService.queryTransfers(query).getContent()));
        assertThat(ids(exported)).containsExactlyElementsOf(pagedOrder);

        // 导出内容与列表口径一致：最久的 36 小时单标记“积压过久”，3 小时单不标记
        List<String> oldestRow = transferService.exportRow(exported.get(0));
        List<String> newestRow = transferService.exportRow(exported.get(exported.size() - 1));
        assertThat(oldestRow).hasSize(BlockTransferService.exportHeaders().length);
        assertThat(oldestRow.get(10)).isEqualTo("积压过久");
        assertThat(newestRow.get(10)).isEmpty();
        assertThat(oldestRow.get(1)).isEqualTo("BLK-WS-001");
    }

    private TransferQueryDTO pendingQuery(String waitSort) {
        TransferQueryDTO query = baseQuery(waitSort);
        query.setStatus(BlockTransfer.STATUS_PENDING);
        return query;
    }

    private TransferQueryDTO allStatusQuery(String waitSort) {
        return baseQuery(waitSort);
    }

    private TransferQueryDTO baseQuery(String waitSort) {
        TransferQueryDTO query = new TransferQueryDTO();
        query.setPage(1);
        query.setSize(10);
        query.setWaitSort(waitSort);
        return query;
    }

    private BlockTransfer savePending(String transferNo, LocalDateTime createTime) {
        BlockTransfer transfer = saveTransfer(transferNo, BlockTransfer.STATUS_PENDING);
        jdbcTemplate.update("UPDATE block_transfer SET create_time = ? WHERE id = ?",
                Timestamp.valueOf(createTime), transfer.getId());
        return transfer;
    }

    private BlockTransfer saveHandled(String transferNo, LocalDateTime createTime, LocalDateTime handleTime) {
        BlockTransfer transfer = saveTransfer(transferNo, BlockTransfer.STATUS_CONFIRMED);
        jdbcTemplate.update("UPDATE block_transfer SET create_time = ?, handle_time = ? WHERE id = ?",
                Timestamp.valueOf(createTime), Timestamp.valueOf(handleTime), transfer.getId());
        return transfer;
    }

    private BlockTransfer saveTransfer(String transferNo, String status) {
        BlockTransfer transfer = new BlockTransfer();
        transfer.setTransferNo(transferNo);
        transfer.setBlockId(block.getId());
        transfer.setFromLineId(fromLine.getId());
        transfer.setToLineId(toLine.getId());
        transfer.setTransferDate(LocalDate.now());
        transfer.setTransferOperator("测试移交人");
        transfer.setStatus(status);
        transfer.setPrintCount(0);
        transfer.setReceiptPrintCount(0);
        return transferRepository.saveAndFlush(transfer);
    }

    private LocalDateTime hoursAgo(int hours) {
        return LocalDateTime.now().minusHours(hours);
    }

    private List<Long> ids(List<BlockTransfer> transfers) {
        return transfers.stream().map(BlockTransfer::getId).toList();
    }

    private Boolean flagOf(List<BlockTransfer> transfers, Long id) {
        return transfers.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElseThrow()
                .getLongWaiting();
    }

    private ProductionLine saveLine(String code, String name) {
        ProductionLine line = new ProductionLine();
        line.setLineCode(code);
        line.setLineName(name);
        return lineRepository.save(line);
    }
}
