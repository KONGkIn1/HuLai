package com.sky.service;

import com.sky.vo.TurnoverReportVO;

import java.time.LocalDate;

public interface ReportService {

    /**
     * 营业额统计数据
     * @param begin
     * @param end
     * @return
     */
    TurnoverReportVO getTurnoverReportVO(LocalDate begin, LocalDate end);

}
