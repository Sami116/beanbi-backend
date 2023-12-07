package com.bean.beanbi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bean.beanbi.mapper.ChartMapper;
import com.bean.beanbi.model.entity.Chart;
import com.bean.beanbi.service.ChartService;
import org.springframework.stereotype.Service;

/**
* @author 13502
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
* @createDate 2023-11-14 18:23:36
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService {

}




