package com.his.modules.inp.task;

import com.his.UnitTestBase;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.ChargeItemDTO;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.inp.entity.InpBed;
import com.his.modules.inp.mapper.InpAdmissionMapper;
import com.his.modules.inp.mapper.InpBedMapper;
import com.his.modules.inp.mapper.InpDailyFeeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 床位费日结任务：条件插入（状态 30 不落账）+ 唯一索引并发兜底。 */
class AdmissionFeeTaskTest extends UnitTestBase {

    private final InpAdmissionMapper admissionMapper = mock(InpAdmissionMapper.class);
    private final InpBedMapper bedMapper = mock(InpBedMapper.class);
    private final InpDailyFeeMapper dailyFeeMapper = mock(InpDailyFeeMapper.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);
    private final com.his.modules.inp.service.InpService inpService =
            mock(com.his.modules.inp.service.InpService.class);

    private final AdmissionFeeTask task = new AdmissionFeeTask(
            admissionMapper, bedMapper, dailyFeeMapper, basedataAppService, inpService);

    @org.junit.jupiter.api.BeforeEach
    void stubFeeTotal() {
        lenient().when(inpService.unpaidFeeTotal(any()))
                .thenReturn(java.math.BigDecimal.ZERO);
    }

    private InpAdmission admission() {
        InpAdmission a = new InpAdmission();
        a.setId(900L);
        a.setStatus(10);
        a.setBedId(30L);
        a.setAdmissionTime(LocalDateTime.now().minusDays(3));
        return a;
    }

    private void stubBedAndPrice() {
        InpBed bed = new InpBed();
        bed.setId(30L);
        bed.setBedNo("A-01");
        bed.setChargeItemId(77L);
        when(bedMapper.selectById(30L)).thenReturn(bed);
        ChargeItemDTO item = new ChargeItemDTO();
        item.setCategory(8);
        item.setItemName("床位费");
        item.setPrice(new BigDecimal("50.00"));
        when(basedataAppService.getChargeItem(77L)).thenReturn(item);
    }

    @Test
    void recordBedFees_usesConditionalInsert() {
        when(admissionMapper.selectList(any())).thenReturn(List.of(admission()));
        when(dailyFeeMapper.selectCount(any())).thenReturn(0L);
        stubBedAndPrice();
        when(dailyFeeMapper.insertBedFeeIfActive(anyLong(), any(LocalDate.class), anyInt(),
                anyLong(), anyString(), any(), any())).thenReturn(1);

        task.recordBedFeesFor(LocalDate.now().minusDays(1));

        verify(dailyFeeMapper).insertBedFeeIfActive(eq(900L), any(LocalDate.class), eq(8),
                eq(30L), contains("A-01"), eq(new BigDecimal("50.00")), eq(new BigDecimal("50.00")));
    }

    @Test
    void recordBedFees_settledDuringRunIsSkippedByConditionalInsert() {
        when(admissionMapper.selectList(any())).thenReturn(List.of(admission()));
        when(dailyFeeMapper.selectCount(any())).thenReturn(0L);
        stubBedAndPrice();
        // 查询在院后并发完成结算：条件插入因 status=30 命中 0 行
        when(dailyFeeMapper.insertBedFeeIfActive(anyLong(), any(LocalDate.class), anyInt(),
                anyLong(), anyString(), any(), any())).thenReturn(0);

        task.recordBedFeesFor(LocalDate.now().minusDays(1));

        verify(dailyFeeMapper, never()).insert(any());
    }

    @Test
    void recordBedFees_duplicateKeyFromConcurrentRunIsSwallowed() {
        when(admissionMapper.selectList(any())).thenReturn(List.of(admission()));
        when(dailyFeeMapper.selectCount(any())).thenReturn(0L);
        stubBedAndPrice();
        when(dailyFeeMapper.insertBedFeeIfActive(anyLong(), any(LocalDate.class), anyInt(),
                anyLong(), anyString(), any(), any())).thenThrow(new DuplicateKeyException("dup"));

        assertDoesNotThrow(() -> task.recordBedFeesFor(LocalDate.now().minusDays(1)));
    }
}
