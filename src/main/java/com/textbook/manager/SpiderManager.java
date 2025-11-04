package com.textbook.manager;

import com.textbook.spider.TextbookSpider;
import com.textbook.processor.DataProcessor;
import com.textbook.spider.TextbookSpider.TextbookInfo;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpiderManager {
    private static final Logger logger = LoggerFactory.getLogger(SpiderManager.class);
    private TextbookSpider spider;
    private DataProcessor processor;

    public SpiderManager() {
        this.spider = new TextbookSpider();
        this.processor = new DataProcessor();
    }

    public void runSpider() {
        long startTime = System.currentTimeMillis();

        try {
            logger.info("开始爬取中小学教材信息...");

            spider.extractTextbookInfo();

            List<TextbookInfo> rawData = spider.getAllData();

            if (rawData != null && !rawData.isEmpty()) {
                List<TextbookInfo> processedData = processor.processData(rawData);

                processor.printStatistics(processedData);

                logger.info("爬虫任务完成！");
            } else {
                logger.warn("未能爬取到数据");
            }

        } catch (Exception e) {
            logger.error("爬虫运行出错", e);
            System.err.println("爬虫运行出错: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                spider.shutdown();
            } catch (Exception e) {
                logger.error("关闭爬虫资源时出错", e);
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            logger.info("总耗时: {} 分钟", TimeUnit.MILLISECONDS.toMinutes(duration));
        }
    }

    public static void main(String[] args) {
        SpiderManager manager = new SpiderManager();
        manager.runSpider();
    }
}