package com.sky.service;

import com.sky.vo.OrderReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import java.time.LocalDate;

public interface ReportService {

    /**
     * 营业额统计数据
     * @param begin
     * @param end
     * @return
     */
    TurnoverReportVO getTurnoverReportVO(LocalDate begin, LocalDate end);


    /**
     * 统计指定时间区间的用户数据
     * @param begin
     * @param end
     * @return
     */
    UserReportVO getUserStatistics(LocalDate begin,LocalDate end);

    /**
     * 统计指定时间区间的订单数据
     * @param begin
     * @param end
     * @return
     */
    OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end);

}
