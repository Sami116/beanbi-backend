package com.bean.beanbi.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bean.beanbi.annotation.AuthCheck;
import com.bean.beanbi.common.BaseResponse;
import com.bean.beanbi.common.DeleteRequest;
import com.bean.beanbi.common.ErrorCode;
import com.bean.beanbi.common.ResultUtils;
import com.bean.beanbi.constant.CommonConstant;
import com.bean.beanbi.constant.UserConstant;
import com.bean.beanbi.exception.BusinessException;
import com.bean.beanbi.exception.ThrowUtils;
import com.bean.beanbi.manager.AiManager;
import com.bean.beanbi.manager.RedisLimiterManager;
import com.bean.beanbi.model.dto.chart.*;
import com.bean.beanbi.model.entity.Chart;
import com.bean.beanbi.model.entity.User;
import com.bean.beanbi.model.vo.BiResponse;
import com.bean.beanbi.mq.MessageProducer;
import com.bean.beanbi.service.ChartService;
import com.bean.beanbi.service.UserService;
import com.bean.beanbi.utils.ExcelUtils;
import com.bean.beanbi.utils.SqlUtils;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 图表接口
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private MessageProducer messageProducer;


    /**
     * 智能分析 （同步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        String chartType = genChartByAiRequest.getChartType();
        String goal = genChartByAiRequest.getGoal();
        String name = genChartByAiRequest.getName();

        User loginUser = userService.getLoginUser(request);
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "分析目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验用户上传的文件大小和后缀
        long fileSize = multipartFile.getSize();
        String filename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1024 * 1024;
        // 校验文件大小
        ThrowUtils.throwIf(fileSize > ONE_MB, ErrorCode.OPERATION_ERROR, "文件大小不能超过1MB");
        // 校验文件后缀
        String suffix = FileUtil.getSuffix(filename);
        final List<String> validFileSuffixList = Arrays.asList("csv"); // 以后可以扩展成支持多种后缀
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.OPERATION_ERROR, "文件后缀有误");
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimiter("genChartByAi_" + loginUser.getId());
        // 用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += ", 请使用" + chartType + "图表类型";
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        // 压缩后的数据
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");
        // 直接调用星火大模型
        String result = aiManager.doChat(userInput.toString());

        String[] splits = result.split("%%%%");
        if (splits.length < 3) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
        }

        String genChart = splits[1].trim();
        String genResult = splits[2].trim();

        // 生成的图表数据保存到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(userGoal);
        chart.setChartType(chartType);
        chart.setChartData(csvData); // 优化点：可以把用户原始数据单独存储在一个新表
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setStatus("succeed");
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表数据保存失败");

        // 返回结果
        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        biResponse.setChartId(chart.getId());

        return ResultUtils.success(biResponse);
    }


    /**
     * 智能分析（异步） 使用线程池
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        String chartType = genChartByAiRequest.getChartType();
        String goal = genChartByAiRequest.getGoal();
        String name = genChartByAiRequest.getName();

        User loginUser = userService.getLoginUser(request);
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "分析目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验用户上传的文件大小和后缀
        long fileSize = multipartFile.getSize();
        String filename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1024 * 1024;
        // 校验文件大小
        ThrowUtils.throwIf(fileSize > ONE_MB, ErrorCode.OPERATION_ERROR, "文件大小不能超过1MB");
        // 校验文件后缀
        String suffix = FileUtil.getSuffix(filename);
        final List<String> validFileSuffixList = Arrays.asList("csv"); // 以后可以扩展成支持多种后缀
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.OPERATION_ERROR, "文件后缀有误");
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimiter("genChartByAi_" + loginUser.getId());
        // 用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += ", 请使用" + chartType + "图表类型";
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        // 压缩后的数据
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 图表数据保存到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(userGoal);
        chart.setChartType(chartType);
        chart.setChartData(csvData); // 优化点：可以把用户原始数据单独存储在一个新表
        chart.setUserId(loginUser.getId());
        chart.setStatus("wait"); //todo 以后改成枚举值

        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表数据保存失败");

        // 向线程池提交任务
        // todo 处理任务队列满了以后的，抛异常的情况
        CompletableFuture.runAsync(() -> {
            // 先修改图表状态为 ”执行中“，执行成功后修改为 “已完成”，保存执行结果，执行失败修改为 “失败”，记录失败信息。
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setStatus("running");
            boolean res = chartService.updateById(updateChart);
            if (!res) {
                handleChartUpdateError(chart.getId(), "更改图表状态为“执行中”失败");
                return;
            }
            // 调用星火大模型
            String result = aiManager.doChat(userInput.toString());

            String[] splits = result.split("%%%%");
            if (splits.length < 3) {
                handleChartUpdateError(chart.getId(), "AI 生成错误");
                return;
            }
            String genChart = splits[1].trim();
            String genResult = splits[2].trim();

            updateChart.setGenChart(genChart);
            updateChart.setGenResult(genResult);
            updateChart.setStatus("succeed");
            boolean updateResult = chartService.updateById(updateChart);
            if (!updateResult) {
                handleChartUpdateError(chart.getId(), "更改图表状态为“成功”失败");
            }
        }, threadPoolExecutor);

        // 返回结果
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());

        return ResultUtils.success(biResponse);
    }

    private void handleChartUpdateError(Long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error(execMessage + "," + chartId);
        }
    }


    /**
     * 智能分析（异步）使用消息队列
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMQ(@RequestPart("file") MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        String chartType = genChartByAiRequest.getChartType();
        String goal = genChartByAiRequest.getGoal();
        String name = genChartByAiRequest.getName();

        User loginUser = userService.getLoginUser(request);
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "分析目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验用户上传的文件大小和后缀
        long fileSize = multipartFile.getSize();
        String filename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1024 * 1024;
        // 校验文件大小
        ThrowUtils.throwIf(fileSize > ONE_MB, ErrorCode.OPERATION_ERROR, "文件大小不能超过1MB");
        // 校验文件后缀
        String suffix = FileUtil.getSuffix(filename);
        final List<String> validFileSuffixList = Arrays.asList("csv"); // 以后可以扩展成支持多种后缀
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.OPERATION_ERROR, "文件后缀有误");
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimiter("genChartByAi_" + loginUser.getId());
        // 用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += ", 请使用" + chartType + "图表类型";
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        // 压缩后的数据
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 图表数据保存到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(userGoal);
        chart.setChartType(chartType);
        chart.setChartData(csvData); // 优化点：可以把用户原始数据单独存储在一个新表
        chart.setUserId(loginUser.getId());
        chart.setStatus("wait"); //todo 以后改成枚举值

        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表数据保存失败");

        // 向消息队列发送消息
        messageProducer.sendMessage(String.valueOf(chart.getId()));

        // 返回结果
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());

        return ResultUtils.success(biResponse);
    }


    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);

        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);

        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest, HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size), getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest, HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size), getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);

        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }

        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        // 拼接查询条件
        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);

        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC), sortField);
        return queryWrapper;
    }

}
