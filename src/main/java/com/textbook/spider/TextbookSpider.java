package com.textbook.spider;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TextbookSpider {
    private static final Logger logger = LoggerFactory.getLogger(TextbookSpider.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;
    private List<TextbookInfo> allData; // 将使用同步方法来保证线程安全
    private ExecutorService executorService;

    public TextbookSpider() {
        this.httpClient = HttpClient.newHttpClient();
        this.allData = Collections.synchronizedList(new ArrayList<>()); // 使用线程安全的集合
        this.executorService = Executors.newFixedThreadPool(10);
    }

    public List<TextbookInfo> getAllData() {
        return allData;
    }

    public void extractTextbookInfo() {
        String baseUrl = "http://www.dzkbw.com";
        String cityListUrl = "http://www.dzkbw.com/city/";

        try {
            Document cityDoc = getDocument(cityListUrl);
            List<CityInfo> cities = extractCityList(cityDoc, baseUrl);

            logger.info("找到 {} 个城市", cities.size());

            CountDownLatch latch = new CountDownLatch(cities.size());

            for (CityInfo city : cities) {
                executorService.submit(() -> {
                    try {
                        processCityData(city, baseUrl);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();

            saveToCSV("全国中小学教材版本.csv");

        } catch (Exception e) {
            logException("爬虫主流程异常", e);
        }
    }

    private Document getDocument(String url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return Jsoup.parse(response.body());

        } catch (Exception e) {
            String errorMessage = "Failed to get document from: " + url;
            logException(errorMessage, e);
            throw new IOException(errorMessage, e);
        }
    }

    private List<CityInfo> extractCityList(Document doc, String baseUrl) {
        List<CityInfo> cities = new ArrayList<>();

        Element cityListDiv = doc.getElementById("citylist");
        if (cityListDiv == null) {
            logger.error("城市列表div元素未找到");
            return cities;
        }
        Elements contents = cityListDiv.children();

        String currentProvince = null;

        for (Element element : contents) {
            if (element.tagName().equals("b")) {
                currentProvince = element.text();
            } else if (element.tagName().equals("a")) {
                String cityName = element.text();
                String href = element.attr("href");
                String fullUrl = baseUrl + href;

                cities.add(new CityInfo(currentProvince, cityName, fullUrl));
            }
        }

        return cities;
    }

    private void processCityData(CityInfo city, String baseUrl) {
        try {
            logger.info("处理城市: {}-{}", city.getProvince(), city.getName());

            Document cityDoc = getDocument(city.getUrl());
            List<DistrictInfo> districts = extractDistrictList(cityDoc, baseUrl);

            for (DistrictInfo district : districts) {
                try {
                    processDistrictData(district, city);

                    Thread.sleep(100);

                } catch (Exception e) {
                    logException("处理区县失败: " + district.getName(), e);
                }
            }

        } catch (Exception e) {
            logException("处理城市失败: " + city.getName(), e);
        }
    }

    private List<DistrictInfo> extractDistrictList(Document doc, String baseUrl) {
        List<DistrictInfo> districts = new ArrayList<>();

        Element districtDiv = doc.selectFirst("div.Districtlist");
        if (districtDiv == null) {
            logger.warn("区县列表div元素未找到");
            return districts;
        }

        // 安全检查ul元素是否存在
        Element ulElement = districtDiv.selectFirst("ul");
        if (ulElement == null) {
            logger.warn("区县列表中的ul元素未找到");
            return districts;
        }

        Elements links = ulElement.select("li a[href]");
        Set<String> filterTexts = Set.of("小学", "初中", "高中", "所有", "更多");

        for (Element link : links) {
            String text = link.text();
            String href = link.attr("href");

            if (filterTexts.contains(text)) {
                continue;
            }

            if (text.contains("区") || text.contains("县") ||
                    text.contains("市") || text.contains("旗")) {

                String fullUrl = baseUrl + href;
                districts.add(new DistrictInfo(text, fullUrl));
            }
        }

        return districts;
    }

    private void processDistrictData(DistrictInfo district, CityInfo city) {
        try {
            Document districtDoc = getDocument(district.getUrl());
            List<TextbookInfo> textbooks = extractTextbookDetails(districtDoc, city, district);

            // 使用线程安全的方式添加数据
            synchronized (allData) {
                allData.addAll(textbooks);
            }

        } catch (Exception e) {
            logException("Failed to process district: " + district.getName(), e);
            // 不再抛出异常，而是记录并继续执行
        }
    }

    private List<TextbookInfo> extractTextbookDetails(Document doc, CityInfo city, DistrictInfo district) {
        List<TextbookInfo> textbooks = new ArrayList<>();

        Elements gradeDivs = doc.select("div.i_d");

        for (Element gradeDiv : gradeDivs) {
            Element gradeH3 = gradeDiv.selectFirst("h3");
            if (gradeH3 == null) {
                logger.warn("年级标题未找到");
                continue;
            }
            String gradeName = gradeH3.text();

            Element divlist = gradeDiv.selectFirst("div.divlist");
            if (divlist == null) {
                continue;
            }

            Elements liElements = divlist.select("li");

            for (Element li : liElements) {
                try {
                    Element versionTag = li.selectFirst("i");
                    String version = versionTag != null ? versionTag.text() : "未知版本";

                    Element titleLink = li.selectFirst("a.ih3");
                    if (titleLink == null) {
                        continue;
                    }

                    String bookTitle = titleLink.text();
                    String bookHref = titleLink.attr("href");

                    String fullUrl = bookHref.startsWith("http") ?
                            bookHref : city.getUrl() + bookHref;

                    TextbookInfo textbook = new TextbookInfo(
                            city.getProvince(),
                            city.getName(),
                            district.getName(),
                            gradeName,
                            bookTitle,
                            version,
                            fullUrl
                    );

                    textbooks.add(textbook);

                } catch (Exception e) {
                    continue;
                }
            }
        }

        return textbooks;
    }

    private void saveToCSV(String filename) {
        // 使用try-with-resources自动关闭资源
        try (FileWriter fileWriter = new FileWriter(filename, StandardCharsets.UTF_8);
             CSVWriter csvWriter = new CSVWriter(fileWriter)) {

            String[] header = {"省", "市", "区/县", "年级", "科目", "版本", "课本链接"};
            csvWriter.writeNext(header);

            // 同步访问allData集合
            synchronized (allData) {
                for (TextbookInfo textbook : allData) {
                    String[] row = {
                            textbook.getProvince(),
                            textbook.getCity(),
                            textbook.getDistrict(),
                            textbook.getGrade(),
                            textbook.getSubject(),
                            textbook.getVersion(),
                            textbook.getBookUrl()
                    };
                    csvWriter.writeNext(row);
                }
            }

            logger.info("数据已保存到: {}", filename);

        } catch (IOException e) {
            logException("Failed to save CSV file", e);
        }
    }

    public void shutdown() {
        try {
            // 优雅关闭线程池
            executorService.shutdown();
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.error("线程池未能正常关闭");
                }
            }
            logger.info("线程池已关闭");
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            logException("线程池关闭时被中断", e);
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
            e.printStackTrace();
            
            // 写入到error_logs.txt文件
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("error_logs.txt", true))) {
                writer.write(jsonError);
                writer.newLine();
            }
        } catch (Exception logError) {
            // 如果日志记录本身失败，直接打印
            System.err.println("日志记录失败: " + logError.getMessage());
            logError.printStackTrace();
        }
    }

    public static class CityInfo {
        private String province;
        private String name;
        private String url;

        public CityInfo(String province, String name, String url) {
            this.province = province;
            this.name = name;
            this.url = url;
        }

        public String getProvince() { return province; }
        public String getName() { return name; }
        public String getUrl() { return url; }
    }

    public static class DistrictInfo {
        private String name;
        private String url;

        public DistrictInfo(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String getName() { return name; }
        public String getUrl() { return url; }
    }

    public static class TextbookInfo {
        private String province;
        private String city;
        private String district;
        private String grade;
        private String subject;
        private String version;
        private String bookUrl;

        public TextbookInfo(String province, String city, String district,
                            String grade, String subject, String version, String bookUrl) {
            this.province = province;
            this.city = city;
            this.district = district;
            this.grade = grade;
            this.subject = subject;
            this.version = version;
            this.bookUrl = bookUrl;
        }

        public String getProvince() { return province; }
        public String getCity() { return city; }
        public String getDistrict() { return district; }
        public String getGrade() { return grade; }
        public String getSubject() { return subject; }
        public String getVersion() { return version; }
        public String getBookUrl() { return bookUrl; }
    }
}