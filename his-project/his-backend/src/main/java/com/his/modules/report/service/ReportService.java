package com.his.modules.report.service;

import com.his.modules.billing.app.BillingAppService;
import com.his.modules.billing.app.DailyRevenueDTO;
import com.his.modules.billing.app.RevenueDetailRowDTO;
import com.his.modules.clinic.app.ClinicAppService;
import com.his.modules.clinic.app.VisitStatDTO;
import com.his.modules.pharmacy.dto.WarningDTO;
import com.his.modules.pharmacy.service.InventoryService;
import com.his.modules.registration.app.DailyStatDTO;
import com.his.modules.registration.app.RegistrationAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计报表服务（T-01~T-05）：仅调用各模块只读查询服务（《01》§4.1），不参与业务事务。
 */
@Service
@RequiredArgsConstructor
public class ReportService {
    private final RegistrationAppService registrationAppService;
    private final ClinicAppService clinicAppService;
    private final BillingAppService billingAppService;
    private final InventoryService inventoryService;

    public List<DailyStatDTO> dailyRegistrations(LocalDate start, LocalDate end) {
        return registrationAppService.dailyRegistrations(start, end);
    }

    public List<DailyStatDTO> deptRegistrationRanking(LocalDate start, LocalDate end) {
        return registrationAppService.deptRegistrationRanking(start, end);
    }

    public List<VisitStatDTO> dailyVisits(LocalDate start, LocalDate end) {
        return clinicAppService.dailyVisits(start, end);
    }

    public List<DailyRevenueDTO> dailyRevenue(LocalDate start, LocalDate end) {
        return billingAppService.dailyRevenue(start, end);
    }

    public List<DailyRevenueDTO.FeeTypeAmount> revenueDistribution(LocalDate start, LocalDate end) {
        return billingAppService.feeTypeDistribution(start, end);
    }

    public List<RevenueDetailRowDTO> revenueDetail(LocalDate start, LocalDate end) {
        return billingAppService.revenueDetailRows(start, end);
    }

    public List<WarningDTO> drugInventory() {
        return inventoryService.inventorySummary();
    }
}
