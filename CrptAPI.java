import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Класс для работы с API Честного знака. Поддерживает ограничение на количество запросов к API.
 */
public class CrptApi {
    private final int requestLimit;
    private final TimeUnit timeUnit;
    private final Map<Long, Integer> requestLog = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    /**
     * Конструктор класса.
     *
     * @param timeUnit     промежуток времени – секунда, минута и т. д.
     * @param requestLimit максимальное количество запросов в указанном промежутке времени
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduler.scheduleAtFixedRate(this::clearRequestLog, 0, 1, timeUnit);
    }

    /**
     * Создание документа для ввода в оборот товара, произведенного в РФ.
     *
     * @param document  Java объект документа
     * @param signature строка с подписью
     */
    public synchronized void createDocument(Object document, String signature) {
        if (!allowRequest()) {
            System.out.println("Request limit reached. Please try after some time.");
            return;
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost("https://ismp.crpt.ru/api/v3/lk/documents/create");
            httpPost.setHeader("Content-Type", "application/json");
            String json = convertObjectToJson(document);
            StringEntity entity = new StringEntity(json);
            httpPost.setEntity(entity);
            CloseableHttpResponse response = null;
            try {
                response = client.execute(httpPost);
                // Обработка ответа здесь. Реализация зависит от фреймворка.
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (response != null) {
                        response.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Преобразует Java объект в строку JSON.
     *
     * @param object Java объект для сериализации
     * @return строка JSON
     * @throws IOException если возникают проблемы при сериализации
     */
    private String convertObjectToJson(Object object) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper.writeValueAsString(object);
    }

    private synchronized boolean allowRequest() {
        long currentTime = System.currentTimeMillis();
        int count = requestLog.getOrDefault(currentTime, 0);
        if (count < requestLimit) {
            requestLog.put(currentTime, count + 1);
            return true;
        }
        return false;
    }

    private synchronized void clearRequestLog() {
        long currentTime = System.currentTimeMillis() - timeUnit.toMillis(requestLimit);
        requestLog.entrySet().removeIf(entry -> entry.getKey() < currentTime);
    }
}
