package com.bufferblock.service;

import com.bufferblock.dto.BufferBlockDTO;
import com.bufferblock.dto.InspectionCreateDTO;
import com.bufferblock.dto.InspectionItemVO;
import com.bufferblock.dto.InspectionOverviewVO;
import com.bufferblock.dto.InspectionQueryDTO;
import com.bufferblock.entity.BlockInspection;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.repository.BlockInspectionRepository;
import com.bufferblock.repository.BlockLineBindingRepository;
import com.bufferblock.repository.BufferBlockRepository;
import com.bufferblock.repository.ProductionLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 班次点检台账：按产线筛选在用挡块、班次打卡落库、
 * 最近一次点检结论同源派生、刷新后条数与列表一致、挂起/未绑定挡块不可点检。
 */
@SpringBootTest
class InspectionServiceTest {

    @Autowired
    private InspectionService inspectionService;
    @Autowired
    private BufferBlockService bufferBlockService;
    @Autowired
    private BlockInspectionRepository inspectionRepository;
    @Autowired
    private BufferBlockRepository blockRepository;
    @Autowired
    private ProductionLineRepository lineRepository;
    @Autowired
    private BlockLineBindingRepository bindingRepository;

    private ProductionLine workshopA;
    private ProductionLine lineA1;
    private ProductionLine lineA2;
    private ProductionLine workshopB;
    private ProductionLine lineB1;

    @BeforeEach
    void setUp() {
        inspectionRepository.deleteAll();
        bindingRepository.deleteAll();
        blockRepository.deleteAll();
        lineRepository.deleteAll();

        workshopA = saveWorkshop("INSP-WA", "一车间");
        lineA1 = saveLine("INSP-LA1", "点检A1号线", workshopA.getId());
        lineA2 = saveLine("INSP-LA2", "点检A2号线", workshopA.getId());
        workshopB = saveWorkshop("INSP-WB", "二车间");
        lineB1 = saveLine("INSP-LB1", "点检B1号线", workshopB.getId());
    }

    @Test
    void overviewOnlyContainsInServiceBlocksBoundToLines() {
        BufferBlock onLine = createBoundBlock("BLK-INSP-1", lineA1.getId());
        BufferBlock suspended = createBoundBlock("BLK-INSP-S", lineA1.getId());
        blockRepository.findById(suspended.getId()).ifPresent(b -> {
            b.setServiceStatus(BufferBlock.STATUS_SUSPENDED);
            blockRepository.save(b);
        });
        BufferBlock unbound = createBlock("BLK-INSP-U");

        InspectionOverviewVO overview = inspectionService.getOverview(new InspectionQueryDTO());

        assertThat(overview.getItems()).extracting(InspectionItemVO::getBlockId)
                .containsExactly(onLine.getId());
        assertThat(overview.getTotalCount()).isEqualTo(1);
        assertThat(overview.getItems()).hasSize(overview.getTotalCount());
        assertThat(overview.getNeverInspectedCount()).isEqualTo(1);
        assertThat(unbound.getId()).isNotNull();
    }

    @Test
    void filterByLeafLineAndWorkshopNode() {
        BufferBlock a1 = createBoundBlock("BLK-INSP-A1", lineA1.getId());
        BufferBlock a2 = createBoundBlock("BLK-INSP-A2", lineA2.getId());
        BufferBlock b1 = createBoundBlock("BLK-INSP-B1", lineB1.getId());

        InspectionQueryDTO leafQuery = new InspectionQueryDTO();
        leafQuery.setLineId(lineA1.getId());
        assertThat(inspectionService.getOverview(leafQuery).getItems())
                .extracting(InspectionItemVO::getBlockId).containsExactly(a1.getId());

        InspectionQueryDTO workshopQuery = new InspectionQueryDTO();
        workshopQuery.setLineId(workshopA.getId());
        assertThat(inspectionService.getOverview(workshopQuery).getItems())
                .extracting(InspectionItemVO::getBlockId)
                .containsExactlyInAnyOrder(a1.getId(), a2.getId());

        assertThat(inspectionService.getOverview(new InspectionQueryDTO()).getItems())
                .extracting(InspectionItemVO::getBlockId)
                .containsExactlyInAnyOrder(a1.getId(), a2.getId(), b1.getId());
    }

    @Test
    void checkInPersistsInspectorShiftResultAndLatestIsDerived() {
        BufferBlock block = createBoundBlock("BLK-INSP-CK", lineA1.getId());
        LocalDateTime firstTime = LocalDateTime.now().minusHours(8);
        checkIn(block.getId(), "MORNING", "USABLE", "点检人甲", firstTime, "早班正常");
        LocalDateTime secondTime = LocalDateTime.now().minusHours(1);
        checkIn(block.getId(), "NIGHT", "UNUSABLE", "点检人乙", secondTime, "晚班发现卡滞");

        BlockInspection latest = inspectionService.getLatest(block.getId());
        assertThat(latest.getShiftCode()).isEqualTo("NIGHT");
        assertThat(latest.getResult()).isEqualTo("UNUSABLE");
        assertThat(latest.getInspector()).isEqualTo("点检人乙");
        assertThat(latest.getNote()).isEqualTo("晚班发现卡滞");
        assertThat(inspectionService.getHistory(block.getId())).hasSize(2);

        // 台账行展示最近一次结论：不可用 1 条，可用 0 条，条数与列表同源
        InspectionOverviewVO overview = inspectionService.getOverview(new InspectionQueryDTO());
        assertThat(overview.getTotalCount()).isEqualTo(1);
        assertThat(overview.getItems()).hasSize(1);
        assertThat(overview.getUnusableCount()).isEqualTo(1);
        assertThat(overview.getUsableCount()).isZero();
        InspectionItemVO row = overview.getItems().get(0);
        assertThat(row.getLastResult()).isEqualTo("UNUSABLE");
        assertThat(row.getLastShiftCode()).isEqualTo("NIGHT");
        assertThat(row.getLastInspector()).isEqualTo("点检人乙");
        assertThat(row.isNeverInspected()).isFalse();

        // 按班次/结论筛选
        InspectionQueryDTO nightQuery = new InspectionQueryDTO();
        nightQuery.setShiftCode("NIGHT");
        assertThat(nightQuery.getShiftCode()).isEqualTo("NIGHT");
        assertThat(inspectionService.getOverview(nightQuery).getItems()).hasSize(1);
        InspectionQueryDTO usableQuery = new InspectionQueryDTO();
        usableQuery.setResult("USABLE");
        assertThat(inspectionService.getOverview(usableQuery).getItems()).isEmpty();
        InspectionQueryDTO neverQuery = new InspectionQueryDTO();
        neverQuery.setResult("NEVER");
        assertThat(inspectionService.getOverview(neverQuery).getItems()).isEmpty();
    }

    @Test
    void countsStayConsistentAcrossReload() {
        BufferBlock usableBlock = createBoundBlock("BLK-INSP-OK", lineA1.getId());
        BufferBlock unusableBlock = createBoundBlock("BLK-INSP-NG", lineA2.getId());
        createBoundBlock("BLK-INSP-NEW", lineB1.getId());

        checkIn(usableBlock.getId(), "MORNING", "USABLE", "点检人甲", LocalDateTime.now(), null);
        checkIn(unusableBlock.getId(), "AFTERNOON", "UNUSABLE", "点检人乙", LocalDateTime.now(), "异常");

        // 模拟刷新/重进：重新调用两次概览接口，条数与列表必须完全一致
        InspectionOverviewVO first = inspectionService.getOverview(new InspectionQueryDTO());
        InspectionOverviewVO second = inspectionService.getOverview(new InspectionQueryDTO());
        for (InspectionOverviewVO vo : java.util.List.of(first, second)) {
            assertThat(vo.getTotalCount()).isEqualTo(3);
            assertThat(vo.getItems()).hasSize(vo.getTotalCount());
            assertThat(vo.getUsableCount()).isEqualTo(1);
            assertThat(vo.getUnusableCount()).isEqualTo(1);
            assertThat(vo.getNeverInspectedCount()).isEqualTo(1);
        }
    }

    @Test
    void suspendedAndUnboundBlocksCannotCheckIn() {
        BufferBlock unbound = createBlock("BLK-INSP-UB");
        assertThatThrownBy(() -> checkIn(unbound.getId(), "MORNING", "USABLE", "点检人甲",
                LocalDateTime.now(), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("绑定产线");

        BufferBlock suspended = createBoundBlock("BLK-INSP-SP", lineA1.getId());
        blockRepository.findById(suspended.getId()).ifPresent(b -> {
            b.setServiceStatus(BufferBlock.STATUS_SUSPENDED);
            blockRepository.save(b);
        });
        assertThatThrownBy(() -> checkIn(suspended.getId(), "MORNING", "USABLE", "点检人甲",
                LocalDateTime.now(), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("挂起待修");
        assertThat(inspectionRepository.count()).isZero();
    }

    @Test
    void invalidCheckInArgumentsAreRejected() {
        BufferBlock block = createBoundBlock("BLK-INSP-IV", lineA1.getId());

        InspectionCreateDTO noShift = dto(block.getId());
        noShift.setShiftCode(null);
        assertThatThrownBy(() -> inspectionService.createInspection(noShift))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("班次");

        InspectionCreateDTO badResult = dto(block.getId());
        badResult.setResult("MAYBE");
        assertThatThrownBy(() -> inspectionService.createInspection(badResult))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("结论");

        InspectionCreateDTO noInspector = dto(block.getId());
        noInspector.setInspector("  ");
        assertThatThrownBy(() -> inspectionService.createInspection(noInspector))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("点检人");

        assertThat(inspectionRepository.count()).isZero();
    }

    @Test
    void blockArchiveCarriesLatestInspectionResult() {
        BufferBlock block = createBoundBlock("BLK-INSP-AR", lineA1.getId());
        assertThat(bufferBlockService.getById(block.getId()).getLastInspectionResult()).isNull();

        checkIn(block.getId(), "MORNING", "USABLE", "点检人甲", LocalDateTime.now().minusHours(5), null);
        checkIn(block.getId(), "NIGHT", "UNUSABLE", "点检人乙", LocalDateTime.now(), "晚班异常");

        BufferBlockDTO archived = bufferBlockService.getById(block.getId());
        assertThat(archived.getLastInspectionResult()).isEqualTo("UNUSABLE");
        assertThat(archived.getLastInspectionShift()).isEqualTo("NIGHT");
        assertThat(archived.getLastInspector()).isEqualTo("点检人乙");
        assertThat(archived.getLastInspectionNote()).isEqualTo("晚班异常");
        assertThat(archived.getLastInspectionTime()).isNotNull();

        assertThat(bufferBlockService.getAllBlocks().stream()
                .filter(dto -> dto.getId().equals(block.getId()))
                .findFirst().orElseThrow().getLastInspectionResult())
                .isEqualTo("UNUSABLE");
    }

    private ProductionLine saveWorkshop(String code, String name) {
        ProductionLine line = new ProductionLine();
        line.setLineCode(code);
        line.setLineName(name);
        return lineRepository.save(line);
    }

    private ProductionLine saveLine(String code, String name, Long parentId) {
        ProductionLine line = new ProductionLine();
        line.setLineCode(code);
        line.setLineName(name);
        line.setParentId(parentId);
        return lineRepository.save(line);
    }

    private BufferBlock createBlock(String code) {
        BufferBlock block = new BufferBlock();
        block.setBlockCode(code);
        block.setAdapterModel("点检测试输送机");
        block.setThickness(BigDecimal.TEN);
        return blockRepository.save(block);
    }

    private BufferBlock createBoundBlock(String code, Long lineId) {
        BufferBlock block = createBlock(code);
        bufferBlockService.bindBlockToLine(block.getId(), lineId, "测试员", 1);
        return block;
    }

    private void checkIn(Long blockId, String shift, String result, String inspector,
                         LocalDateTime time, String note) {
        InspectionCreateDTO dto = dto(blockId);
        dto.setShiftCode(shift);
        dto.setResult(result);
        dto.setInspector(inspector);
        dto.setInspectionTime(time);
        dto.setNote(note);
        inspectionService.createInspection(dto);
    }

    private InspectionCreateDTO dto(Long blockId) {
        InspectionCreateDTO dto = new InspectionCreateDTO();
        dto.setBlockId(blockId);
        dto.setShiftCode("MORNING");
        dto.setResult("USABLE");
        dto.setInspector("点检人甲");
        dto.setInspectionTime(LocalDateTime.now());
        return dto;
    }
}
