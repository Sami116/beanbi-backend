package com.bean.beanbi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bean.beanbi.model.entity.Chart;

import java.util.List;
import java.util.Map;

/**
 * @author 13502
 * @description 针对表【chart(图表信息表)】的数据库操作Mapper
 * @createDate 2023-11-14 18:23:36
 * @Entity com.bean.beanbi.model.entity.Chart
 */
public interface ChartMapper extends BaseMapper<Chart> {


    List<Map<String, Object>> queryChartData(String querySql);

}




