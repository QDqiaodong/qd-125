package com.bufferblock.service;

import com.bufferblock.dto.GaugeBlockedVO;
import com.bufferblock.dto.GaugeCalibrationCreateDTO;
import com.bufferblock.dto.GaugeToolCreateDTO;
import com.bufferblock.dto.GaugeToolItemVO;
import com.bufferblock.dto.GaugeToolOverviewVO;
import com.bufferblock.dto.GaugeToolQueryDTO;
import com.bufferblock.entity.GaugeCalibration;
import com.bufferblock.entity.GaugeTool;
import com.bufferblock.exception.GaugeCalibrationBlockedException;
import com.bufferblock.repository.GaugeCalibrationRepository;
import com.bufferblock.repository.GaugeToolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 点检工装校准台：卡尺/塞尺/百分表台账、校准登记、超期/不合格拦截口径，
 * 以及校准台条数与班次点检打卡拦截清单同源一致。
 */
@SpringBootTest
class GaugeCalibrationServiceTest {

    @Autowired
    private GaugeCalibrationService gaugeService;
    @Autowired
    private GaugeToolRepository toolRepository;
    @Autowired
    private GaugeCalibrationRepository calibrationRepository;

    @BeforeEach
    void setUp() {
        calibrationRepository.deleteAll();
        toolRepository.deleteAll();
    }

    @Test
    void ledgerRegistersCodeDueDateAndTeam() {
        GaugeTool caliper = createTool("KC-001", GaugeTool.TYPE_CALIPER,
                LocalDate.now().plusDays(30), "总装一班");

        GaugeToolOverviewVO overview = gaugeService.getOverview(new GaugeToolQueryDTO());
        assertThat(overview.getItems()).hasSize(1);
        GaugeToolItemVO row = overview.getItems().get(0);
        assertThat(row.getToolCode()).isEqualTo("KC-001");
        assertThat(row.getToolType()).isEqualTo(GaugeTool.TYPE_CALIPER);
        assertThat(row.getToolTypeName()).isEqualTo("卡尺");
        assertThat(row.getKeeperTeam()).isEqualTo("总装一班");
        assertThat(row.getCalibrationDueDate()).isEqualTo(caliper.getCalibrationDueDate());
        assertThat(row.getCalibrationStatus()).isEqualTo(GaugeCalibrationService.STATUS_NORMAL);
        assertThat(row.isBlocked()).isFalse();
        assertThat(overview.getBlockedCount()).isZero();
        assertThat(overview.getNormalCount()).isEqualTo(1);
    }

    @Test
    void allThreeToolTypesSupported() {
        createTool("KC-100", GaugeTool.TYPE_CALIPER, LocalDate.now().plusDays(10), "甲班");
        createTool("SC-100", GaugeTool.TYPE_FEELER, LocalDate.now().plusDays(10), "甲班");
        createTool("BFB-100", GaugeTool.TYPE_DIAL_INDICATOR, LocalDate.now().plusDays(10), "甲班");

        GaugeToolOverviewVO overview = gaugeService.getOverview(new GaugeToolQueryDTO());
        assertThat(overview.getTotalCount()).isEqualTo(3);
        assertThat(overview.getItems()).extracting(GaugeToolItemVO::getToolTypeName)
                .containsExactlyInAnyOrder("卡尺", "塞尺", "百分表");
    }

    @Test
    void dueTodayIsNotOverdueButOneDayPastIs() {
        GaugeTool dueToday = createTool("KC-D0", GaugeTool.TYPE_CALIPER, LocalDate.now(), "甲班");
        GaugeTool dueYesterday = createTool("KC-D1", GaugeTool.TYPE_CALIPER, LocalDate.now().minusDays(1), "甲班");

        GaugeToolOverviewVO overview = gaugeService.getOverview(new GaugeToolQueryDTO());
        assertThat(overview.getBlockedCount()).isEqualTo(1);
        assertThat(overview.getBlockedItems()).extracting(GaugeToolItemVO::getToolCode)
                .containsExactly("KC-D1");
        GaugeToolItemVO overdue = overview.getBlockedItems().get(0);
        assertThat(overdue.getCalibrationStatus()).isEqualTo(GaugeCalibrationService.STATUS_OVERDUE);
        assertThat(overdue.getBlockedReason()).isEqualTo(GaugeCalibrationService.REASON_OVERDUE);
        assertThat(overdue.getOverdueDays()).isEqualTo(1L);
        // 当天到期不算超期
        assertThat(overview.getItems()).filteredOn(i -> i.getToolCode().equals("KC-D0"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.isBlocked()).isFalse();
                    assertThat(row.getCalibrationStatus()).isNotEqualTo(GaugeCalibrationService.STATUS_OVERDUE);
                });
        assertThat(dueToday.getId()).isNotNull();
        assertThat(dueYesterday.getId()).isNotNull();
    }

    @Test
    void failedCalibrationBlocksEvenWhenDueDateInFuture() {
        GaugeTool tool = createTool("SC-F1", GaugeTool.TYPE_FEELER,
                LocalDate.now().plusDays(60), "总装二班");
        calibrate(tool.getId(), GaugeCalibration.RESULT_FAIL,
                LocalDate.now().minusDays(2), LocalDate.now().plusDays(60));

        GaugeToolOverviewVO overview = gaugeService.getOverview(new GaugeToolQueryDTO());
        // 最近结论不合格优先判为 FAIL 并拦截
        assertThat(overview.getBlockedCount()).isEqualTo(1);
        GaugeToolItemVO row = overview.getBlockedItems().get(0);
        assertThat(row.getCalibrationStatus()).isEqualTo(GaugeCalibrationService.STATUS_FAIL);
        assertThat(row.getBlockedReason()).isEqualTo(GaugeCalibrationService.REASON_FAIL);
        assertThat(row.getLastResult()).isEqualTo(GaugeCalibration.RESULT_FAIL);
        assertThat(overview.getFailCount()).isEqualTo(1);

        // 打卡被拦住，明细列出编号
        assertThatThrownBy(gaugeService::assertNoBlockedTools)
                .isInstanceOf(GaugeCalibrationBlockedException.class)
                .hasMessageContaining("SC-F1")
                .satisfies(ex -> {
                    GaugeBlockedVO detail =
                            (GaugeBlockedVO) ((GaugeCalibrationBlockedException) ex).getDetail();
                    assertThat(detail.getCount()).isEqualTo(1);
                    assertThat(detail.getCodes()).containsExactly("SC-F1");
                });
    }

    @Test
    void passCalibrationClearsBlockAndSameShiftCheckInPasses() {
        // 初始即超期
        GaugeTool tool = createTool("BFB-O1", GaugeTool.TYPE_DIAL_INDICATOR,
                LocalDate.now().minusDays(5), "总装三班");
        GaugeTool other = createTool("KC-OK", GaugeTool.TYPE_CALIPER,
                LocalDate.now().plusDays(30), "总装三班");

        // 打卡先被拦住
        assertThatThrownBy(gaugeService::assertNoBlockedTools)
                .isInstanceOf(GaugeCalibrationBlockedException.class);

        // 校准合格：到期日同步为下次应校日
        GaugeCalibration pass = calibrate(tool.getId(), GaugeCalibration.RESULT_PASS,
                LocalDate.now(), LocalDate.now().plusYears(1));
        GaugeTool reloaded = toolRepository.findById(tool.getId()).orElseThrow();
        assertThat(reloaded.getCalibrationDueDate()).isEqualTo(pass.getNextDueDate());

        // 校准台刷新：拦截条数归零
        GaugeToolOverviewVO overview = gaugeService.getOverview(new GaugeToolQueryDTO());
        assertThat(overview.getBlockedCount()).isZero();
        assertThat(overview.getBlockedItems()).isEmpty();

        // 同一班次再打卡（再次执行同一闸门）放行
        gaugeService.assertNoBlockedTools();
        // 合格工装在期
        assertThat(overview.getItems()).filteredOn(i -> i.getToolCode().equals("BFB-O1"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.isBlocked()).isFalse();
                    assertThat(row.getCalibrationStatus()).isEqualTo(GaugeCalibrationService.STATUS_NORMAL);
                    assertThat(row.getLastResult()).isEqualTo(GaugeCalibration.RESULT_PASS);
                });
        assertThat(other.getId()).isNotNull();
    }

    @Test
    void blockedCountMatchesCodesAndSurvivesReloadAndFilters() {
        createTool("KC-A", GaugeTool.TYPE_CALIPER, LocalDate.now().minusDays(3), "甲班");
        GaugeTool failTool = createTool("SC-B", GaugeTool.TYPE_FEELER, LocalDate.now().plusDays(40), "乙班");
        calibrate(failTool.getId(), GaugeCalibration.RESULT_FAIL, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(40));
        createTool("BFB-C", GaugeTool.TYPE_DIAL_INDICATOR, LocalDate.now().plusDays(20), "甲班");

        // 刷新两次：条数恒等于明细条数与编号条数
        for (int i = 0; i < 2; i++) {
            GaugeToolOverviewVO overview = gaugeService.getOverview(new GaugeToolQueryDTO());
            assertThat(overview.getBlockedCount()).isEqualTo(2);
            assertThat(overview.getBlockedItems()).hasSize(2);
            assertThat(overview.getBlockedItems()).extracting(GaugeToolItemVO::getToolCode)
                    .containsExactly("KC-A", "SC-B");
        }

        // 只看卡尺 / 只看在期合格 / 关键字筛选，都不改变全局拦截清单口径
        GaugeToolQueryDTO caliperQuery = new GaugeToolQueryDTO();
        caliperQuery.setToolType(GaugeTool.TYPE_CALIPER);
        GaugeToolOverviewVO filtered = gaugeService.getOverview(caliperQuery);
        assertThat(filtered.getItems()).hasSize(1);
        assertThat(filtered.getBlockedCount()).isEqualTo(2);
        assertThat(filtered.getBlockedItems()).hasSize(2);

        GaugeToolQueryDTO normalQuery = new GaugeToolQueryDTO();
        normalQuery.setStatus("NORMAL");
        GaugeToolOverviewVO normalOnly = gaugeService.getOverview(normalQuery);
        assertThat(normalOnly.getItems()).hasSize(1);
        assertThat(normalOnly.getBlockedCount()).isEqualTo(2);

        // 拦截清单（打卡闸门）与校准台同源
        GaugeBlockedVO blocked = gaugeService.getBlockedTools();
        assertThat(blocked.getCount()).isEqualTo(2);
        assertThat(blocked.getCodes()).containsExactly("KC-A", "SC-B");
    }

    @Test
    void disabledToolIsKeptInLedgerButDoesNotBlock() {
        GaugeTool tool = createTool("KC-X", GaugeTool.TYPE_CALIPER,
                LocalDate.now().minusDays(10), "甲班");
        GaugeToolCreateDTO disable = new GaugeToolCreateDTO();
        disable.setId(tool.getId());
        disable.setToolCode("KC-X");
        disable.setToolType(GaugeTool.TYPE_CALIPER);
        disable.setCalibrationDueDate(tool.getCalibrationDueDate());
        disable.setKeeperTeam("甲班");
        disable.setDisabled(1);
        gaugeService.updateTool(disable);

        GaugeToolOverviewVO overview = gaugeService.getOverview(new GaugeToolQueryDTO());
        assertThat(overview.getDisabledCount()).isEqualTo(1);
        assertThat(overview.getBlockedCount()).isZero();
        gaugeService.assertNoBlockedTools();

        // 停用工装不能登记校准
        GaugeCalibrationCreateDTO cal = validCal(tool.getId());
        assertThatThrownBy(() -> gaugeService.createCalibration(cal))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("停用");
    }

    @Test
    void duplicateCodeAndInvalidArgumentsRejected() {
        createTool("KC-DUP", GaugeTool.TYPE_CALIPER, LocalDate.now().plusDays(5), "甲班");
        GaugeToolCreateDTO dup = new GaugeToolCreateDTO();
        dup.setToolCode("KC-DUP");
        dup.setToolType(GaugeTool.TYPE_CALIPER);
        dup.setCalibrationDueDate(LocalDate.now().plusDays(5));
        dup.setKeeperTeam("甲班");
        assertThatThrownBy(() -> gaugeService.createTool(dup))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("已存在");

        GaugeToolCreateDTO noType = new GaugeToolCreateDTO();
        noType.setToolCode("KC-NT");
        noType.setToolType("MICROMETER");
        noType.setCalibrationDueDate(LocalDate.now().plusDays(5));
        noType.setKeeperTeam("甲班");
        assertThatThrownBy(() -> gaugeService.createTool(noType))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("类型");

        GaugeToolCreateDTO noTeam = new GaugeToolCreateDTO();
        noTeam.setToolCode("KC-NTM");
        noTeam.setToolType(GaugeTool.TYPE_CALIPER);
        noTeam.setCalibrationDueDate(LocalDate.now().plusDays(5));
        noTeam.setKeeperTeam("  ");
        assertThatThrownBy(() -> gaugeService.createTool(noTeam))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("保管班组");

        assertThat(toolRepository.count()).isEqualTo(1);
    }

    @Test
    void invalidCalibrationDatesRejected() {
        GaugeTool tool = createTool("KC-IV", GaugeTool.TYPE_CALIPER,
                LocalDate.now().plusDays(5), "甲班");
        GaugeCalibrationCreateDTO cal = validCal(tool.getId());
        cal.setNextDueDate(LocalDate.now().minusDays(1));
        assertThatThrownBy(() -> gaugeService.createCalibration(cal))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("下次应校日期");
        assertThat(calibrationRepository.count()).isZero();
    }

    private GaugeTool createTool(String code, String type, LocalDate dueDate, String team) {
        GaugeToolCreateDTO dto = new GaugeToolCreateDTO();
        dto.setToolCode(code);
        dto.setToolType(type);
        dto.setCalibrationDueDate(dueDate);
        dto.setKeeperTeam(team);
        return gaugeService.createTool(dto);
    }

    private GaugeCalibration calibrate(Long toolId, String result, LocalDate date, LocalDate nextDue) {
        GaugeCalibrationCreateDTO dto = new GaugeCalibrationCreateDTO();
        dto.setToolId(toolId);
        dto.setCalibrationDate(date);
        dto.setResult(result);
        dto.setValidUntil(nextDue);
        dto.setNextDueDate(nextDue);
        dto.setCalibrator("校准员甲");
        return gaugeService.createCalibration(dto);
    }

    private GaugeCalibrationCreateDTO validCal(Long toolId) {
        GaugeCalibrationCreateDTO dto = new GaugeCalibrationCreateDTO();
        dto.setToolId(toolId);
        dto.setCalibrationDate(LocalDate.now());
        dto.setResult(GaugeCalibration.RESULT_PASS);
        dto.setValidUntil(LocalDate.now().plusYears(1));
        dto.setNextDueDate(LocalDate.now().plusYears(1));
        dto.setCalibrator("校准员甲");
        return dto;
    }
}
