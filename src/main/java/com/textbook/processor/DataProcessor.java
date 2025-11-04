package com.textbook.processor;

import com.textbook.spider.TextbookSpider.TextbookInfo;
import java.util.*;
import java.util.stream.Collectors;
import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DataProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DataProcessor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public List<TextbookInfo> processData(List<TextbookInfo> rawData) {
        try {
            if (rawData == null || rawData.isEmpty()) {
                logger.warn("原始数据为空");
                return new ArrayList<>();
            }

            logger.info("开始处理数据...");
            logger.info("原始数据量: {}", rawData.size());

            Set<String> seen = new HashSet<>();
            List<TextbookInfo> deduplicatedData = new ArrayList<>();

            for (TextbookInfo textbook : rawData) {
                try {
                    if (textbook == null) {
                        logger.warn("发现空的教材信息对象，跳过处理");
                        continue;
                    }
                    
                    String key = textbook.getProvince() + "|" + textbook.getCity() + "|" +
                            textbook.getDistrict() + "|" + textbook.getGrade() + "|" +
                            textbook.getSubject() + "|" + textbook.getVersion();

                    if (!seen.contains(key)) {
                        seen.add(key);
                        deduplicatedData.add(textbook);
                    }
                } catch (Exception e) {
                    logException("处理单个教材信息时出错", e);
                }
            }

            logger.info("去重后数据量: {}", deduplicatedData.size());

            List<TextbookInfo> cleanedData = cleanData(deduplicatedData);
            List<TextbookInfo> sortedData = sortData(cleanedData);

            logger.info("数据处理完成");
            return sortedData;
        } catch (Exception e) {
            logException("数据处理过程出错", e);
            return new ArrayList<>();
        }
    }

    private List<TextbookInfo> cleanData(List<TextbookInfo> data) {
        try {
            int beforeSize = data.size();
            List<TextbookInfo> cleaned = data.stream()
                    .filter(textbook -> {
                        return textbook != null &&
                               textbook.getProvince() != null && !textbook.getProvince().isEmpty() &&
                               textbook.getCity() != null && !textbook.getCity().isEmpty() &&
                               textbook.getSubject() != null && !textbook.getSubject().isEmpty();
                    })
                    .collect(Collectors.toList());
            int afterSize = cleaned.size();
            if (beforeSize != afterSize) {
                logger.info("数据清洗: 清洗前 {} 条，清洗后 {} 条，移除了 {} 条无效数据", 
                           beforeSize, afterSize, beforeSize - afterSize);
            }
            return cleaned;
        } catch (Exception e) {
            logException("数据清洗过程出错", e);
            return new ArrayList<>();
        }
    }

    private List<TextbookInfo> sortData(List<TextbookInfo> data) {
        try {
            List<String> gradeOrder = Arrays.asList(
                    "一年级", "二年级", "三年级", "四年级", "五年级", "六年级",
                    "七年级", "八年级", "九年级", "高一", "高二", "高三"
            );

            return data.stream()
                    .sorted((t1, t2) -> {
                        try {
                            if (t1 == null || t2 == null) {
                                return 0;
                            }
                            
                            int provinceCompare = t1.getProvince() != null && t2.getProvince() != null ? 
                                    t1.getProvince().compareTo(t2.getProvince()) : 0;
                            if (provinceCompare != 0) return provinceCompare;

                            int cityCompare = t1.getCity() != null && t2.getCity() != null ? 
                                    t1.getCity().compareTo(t2.getCity()) : 0;
                            if (cityCompare != 0) return cityCompare;

                            int grade1Index = t1.getGrade() != null ? gradeOrder.indexOf(t1.getGrade()) : -1;
                            int grade2Index = t2.getGrade() != null ? gradeOrder.indexOf(t2.getGrade()) : -1;

                            if (grade1Index == -1) grade1Index = Integer.MAX_VALUE;
                            if (grade2Index == -1) grade2Index = Integer.MAX_VALUE;

                            return Integer.compare(grade1Index, grade2Index);
                        } catch (Exception e) {
                            logException("数据排序过程出错", e);
                            return 0;
                        }
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logException("数据排序过程出错", e);
            return new ArrayList<>(data);
        }
    }

    public void printStatistics(List<TextbookInfo> data) {
        try {
            if (data == null || data.isEmpty()) {
                logger.warn("没有数据可统计");
                System.out.println("没有数据可统计");
                return;
            }

            logger.info("开始生成统计信息...");
            
            System.out.println("=".repeat(60));
            System.out.println("数据统计信息");
            System.out.println("=".repeat(60));

            System.out.println("总记录数: " + data.size());
            logger.info("总记录数: {}", data.size());

            Map<String, Long> provinceCount = data.stream()
                    .filter(t -> t != null)
                    .collect(Collectors.groupingBy(
                            TextbookInfo::getProvince,
                            Collectors.counting()
                    ));

            System.out.println("涉及省份: " + provinceCount.size() + " 个");
            logger.info("涉及省份: {} 个", provinceCount.size());

            Map<String, Long> gradeCount = data.stream()
                    .filter(t -> t != null)
                    .collect(Collectors.groupingBy(
                            TextbookInfo::getGrade,
                            Collectors.counting()
                    ));

            System.out.println("\n年级分布:");
            gradeCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> {
                        System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " 条");
                        logger.info("年级 {}: {} 条", entry.getKey(), entry.getValue());
                    });

            System.out.println("=".repeat(60));
            logger.info("统计信息生成完成");
        } catch (Exception e) {
            logException("生成统计信息时出错", e);
        }
    }
    
    /**
     * 结构化记录异常信息到日志文件
     */
    private void logException(String message, Exception e) {
        try {
            // 构建结构化异常信息
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("timestamp", System.currentTimeMillis());
            errorInfo.put("message", message);
            errorInfo.put("exception", e.getClass().getName());
            errorInfo.put("errorMessage", e.getMessage());
            
            // 获取堆栈信息
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            errorInfo.put("stackTrace", sw.toString());
            
            // 转换为JSON格式并记录
            String jsonError = objectMapper.writeValueAsString(errorInfo);
            logger.error(jsonError);
            
            // 同时打印到控制台
            System.err.println("发生异常: " + message);
            
            // 写入到error_logs.txt文件
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("error_logs.txt", true))) {
                writer.write(jsonError);
                writer.newLine();
            }
        } catch (Exception logError) {
            // 如果日志记录本身失败，直接打印
            System.err.println("日志记录失败: " + logError.getMessage());
        }
    }
}