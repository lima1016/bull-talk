package com.lima.consoleservice.common.connection;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ElasticsearchConnection {

  private static final ElasticsearchConnection INSTANCE;

  private final ElasticsearchClient client;
  private final ElasticsearchTransport transport;
  private final RestClient restClient;

  private static final String[] ELASTICSEARCH_HOSTS = {"localhost:9200"};
  private static final String SCHEME = "http";
  private static final int CONNECTION_TIMEOUT = 5000;
  private static final int SOCKET_TIMEOUT = 30000;

  static {
    try {
      INSTANCE = new ElasticsearchConnection();
    } catch (Exception e) {
      log.error("Failed to initialize ElasticsearchConnection singleton", e);
      throw new IllegalStateException("Cannot initialize Elasticsearch connection", e);
    }
  }

  private ElasticsearchConnection() {
    RestClient initializedRestClient;
    try {
      initializedRestClient = initializeRestClient();
    } catch (Exception e) {
      log.error("Failed to initialize Elasticsearch RestClient", e);
      throw new IllegalStateException("Cannot initialize Elasticsearch connection", e);
    }
    this.restClient = initializedRestClient;
    this.transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
    this.client = new ElasticsearchClient(transport);
  }

  public static ElasticsearchConnection getInstance() {
    return INSTANCE;
  }

  private RestClient initializeRestClient() {
    HttpHost[] hosts = Arrays.stream(ELASTICSEARCH_HOSTS)
        .map(host -> {
          String[] parts = host.split(":");
          return new HttpHost(parts[0], Integer.parseInt(parts[1]), SCHEME);
        })
        .toArray(HttpHost[]::new);

    return RestClient.builder(hosts)
        .setRequestConfigCallback(builder ->
            builder.setConnectTimeout(CONNECTION_TIMEOUT)
                .setSocketTimeout(SOCKET_TIMEOUT))
        .build();
  }

  public synchronized BulkResponse bulkInsert(String index, List<String> jsonDataList) {
    List<BulkOperation> operations = new ArrayList<>();
    log.info("index: {}, insert start", index);
    try {
      if (jsonDataList.isEmpty()) {
        throw new IllegalArgumentException("jsonDataList cannot be empty");
      }

      for (String jsonData : jsonDataList) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> jsonMap = objectMapper.readValue(
            jsonData, new TypeReference<Map<String, Object>>() {}
        );

        IndexOperation<Object> indexOp = IndexOperation.of(op -> op
            .index(index)
            .document(jsonMap)
        );

        // BulkOperation 객체에 IndexOperation 추가
        operations.add(BulkOperation.of(op -> op.index(indexOp)));  // BulkOperation 생성 시 인덱스 작업 추가
      }

      BulkRequest bulkRequest = BulkRequest.of(req -> req.operations(operations)  // 생성된 BulkOperation 목록 전달
      );

      return client.bulk(bulkRequest);

    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new RuntimeException("Failed to perform bulk insert", e);
    }
  }

  public synchronized List<Map<String, Object>> searchMatchAll(String index) {
    List<Map<String, Object>> results = new ArrayList<>();
    try {
      SearchResponse<Map> searchResponse = client.search(s -> s
              .index(index)
              .query(q -> q.matchAll(ma -> ma))
              .size(10000), // 최대 10,000개 문서 반환 (필요 시 조정)
          Map.class
      );

      for (Hit<Map> hit : searchResponse.hits().hits()) {
        results.add(hit.source());
      }

      log.info("Search completed for index: {}, found {} results", index, results.size());
      return results;

    } catch (Exception e) {
      log.error("Failed to search data: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to search data", e);
    }
  }

  public synchronized List<Map<String, Object>> searchMatch(String index, String queryField, String queryValue) {
    List<Map<String, Object>> results = new ArrayList<>();
    try {
      // 날짜 범위 설정: queryValue를 기반으로 하루의 시작과 끝 지정
      String startOfDay = queryValue + " 00:00:00";
      String endOfDay = queryValue + " 23:59:59";

      // SearchRequest 생성
      SearchResponse<Map> searchResponse = client.search(s -> s
              .index(index) // 조회할 인덱스 지정
              .query(q -> q
                  .range(r -> r
                      .field(queryField) // 검색할 필드 (예: "timestamp")
                      .gte(JsonData.of(startOfDay))   // >= queryValue 00:00:00
                      .lte(JsonData.of(endOfDay))     // <= queryValue 23:59:59
                      .format("yyyy-MM-dd HH:mm:ss") // 매핑과 일치하는 날짜 형식
                  )
              ),
          Map.class // 응답을 Map으로 매핑
      );

      // 검색 결과 처리
      for (Hit<Map> hit : searchResponse.hits().hits()) {
        results.add(hit.source()); // 검색된 문서의 소스 데이터를 리스트에 추가
      }

      log.info("Search completed for index: {}, found {} results", index, results.size());
      return results;

    } catch (Exception e) {
      log.error("Failed to search data: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to search data", e);
    }
  }

  @PreDestroy
  public void closeClient() {
    try {
      if (transport != null) {
        transport.close();
      }
      if (restClient != null) {
        restClient.close();
      }
    } catch (IOException e) {
      throw new RuntimeException("Error closing Elasticsearch client", e);
    }
  }
}
