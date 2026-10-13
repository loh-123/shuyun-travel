package com.shuyun.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 后台行程统计 Mapper
 *
 * <p>聚合查询，只读。</p>
 */
public interface AdminTripMapper {

    /** 行程总数 */
    @Select("SELECT COUNT(*) FROM user_trip WHERE deleted = 0")
    long countTrips();

    /** 今日新增行程 */
    @Select("SELECT COUNT(*) FROM user_trip WHERE deleted = 0 AND DATE(create_time) = CURDATE()")
    long countTripsToday();

    /** 昨日新增行程（用于环比） */
    @Select("SELECT COUNT(*) FROM user_trip WHERE deleted = 0 AND DATE(create_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    long countTripsYesterday();

    /** 指定日期的平均天数 */
    @Select("SELECT AVG(days) FROM user_trip WHERE deleted = 0 AND days IS NOT NULL AND DATE(create_time) = #{date}")
    Double avgDaysOn(@Param("date") String date);

    /** 指定日期的平均景点数（每个行程的每日明细条数均值） */
    @Select("SELECT AVG(c) FROM (SELECT COUNT(*) AS c FROM user_trip_day d JOIN user_trip t ON d.trip_id = t.id "
            + "WHERE d.deleted = 0 AND t.deleted = 0 AND DATE(t.create_time) = #{date} GROUP BY d.trip_id) x")
    Double avgScenicOn(@Param("date") String date);

    /** 平均天数（全量） */
    @Select("SELECT AVG(days) FROM user_trip WHERE deleted = 0 AND days IS NOT NULL")
    Double avgDays();

    /** 平均景点数（全量） */
    @Select("SELECT AVG(c) FROM (SELECT COUNT(*) AS c FROM user_trip_day WHERE deleted = 0 GROUP BY trip_id) t")
    Double avgScenicPerTrip();
}
