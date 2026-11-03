package com.smartats.infrastructure.vector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Milvus vector storage service
 * <p>
 * Manages Milvus collection creation, vector insertion, similarity search and deletion.
 * <p>
 * Collection Schema: candidate_vectors
 * - candidate_id (INT64, PK) — the candidate ID
 * - embedding (FLOAT_VECTOR, dim=1024) — the candidate embedding vector
 * - candidate_name (VARCHAR, max=200) — redundant name field (used when rendering results)
 * <p>
 * Index: IVF_FLAT (suited to small and medium datasets < 1 million, with a high recall rate)
 * Metric: COSINE (cosine similarity, suited to textual semantic matching)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final MilvusClientV2 milvusClient;
    private final Gson gson = new Gson();

    /** Collection name */
    public static final String COLLECTION_NAME = "candidate_vectors";

    /** Vector dimension (matches the embedding-3 output) */
    private static final int VECTOR_DIMENSION = EmbeddingService.EMBEDDING_DIMENSION;

    /** IVF_FLAT cluster count (recommended: 4 * sqrt(N), based on a 10,000-row dataset) */
    private static final int NLIST = 128;

    /**
     * Initialise the collection when the application starts (an idempotent operation)
     */
    @PostConstruct
    public void initCollection() {
        try {
            boolean exists = milvusClient.hasCollection(
                    HasCollectionReq.builder()
                            .collectionName(COLLECTION_NAME)
                            .build()
            );

            if (exists) {
                log.info("Milvus collection already exists: {}", COLLECTION_NAME);
                loadCollection();
                return;
            }

            // Define the schema (using AddFieldReq)
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .build();

            schema.addField(AddFieldReq.builder()
                    .fieldName("candidate_id")
                    .dataType(DataType.Int64)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());

            schema.addField(AddFieldReq.builder()
                    .fieldName("embedding")
                    .dataType(DataType.FloatVector)
                    .dimension(VECTOR_DIMENSION)
                    .build());

            schema.addField(AddFieldReq.builder()
                    .fieldName("candidate_name")
                    .dataType(DataType.VarChar)
                    .maxLength(200)
                    .build());

            // Define the index
            IndexParam indexParam = IndexParam.builder()
                    .fieldName("embedding")
                    .indexType(IndexParam.IndexType.IVF_FLAT)
                    .metricType(IndexParam.MetricType.COSINE)
                    .extraParams(Map.of("nlist", NLIST))
                    .build();

            // Create the collection
            CreateCollectionReq createReq = CreateCollectionReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .collectionSchema(schema)
                    .indexParams(List.of(indexParam))
                    .build();

            milvusClient.createCollection(createReq);
            log.info("Milvus collection created successfully: {}", COLLECTION_NAME);

            loadCollection();

        } catch (Exception e) {
            log.error("Failed to initialise the Milvus collection: {}", COLLECTION_NAME, e);
            throw new IllegalStateException("Failed to initialise the Milvus collection", e);
        }
    }

    /**
     * Insert or update a candidate vector (Upsert semantics)
     *
     * @param candidateId   the candidate ID
     * @param candidateName the candidate name
     * @param embedding     the 1024-dimensional embedding vector
     * @return the Milvus internal ID as a string
     */
    public String upsertVector(Long candidateId, String candidateName, List<Float> embedding) {
        log.info("Upserting the candidate vector: candidateId={}", candidateId);

        JsonObject row = new JsonObject();
        row.addProperty("candidate_id", candidateId);
        row.add("embedding", gson.toJsonTree(embedding));
        row.addProperty("candidate_name", candidateName != null ? candidateName : "");

        UpsertResp resp = milvusClient.upsert(UpsertReq.builder()
                .collectionName(COLLECTION_NAME)
                .data(List.of(row))
                .build());

        log.info("Candidate vector upserted successfully: candidateId={}, upsertCount={}",
                candidateId, resp.getUpsertCnt());
        return String.valueOf(candidateId);
    }

    /**
     * Insert candidate vectors in bulk
     *
     * @param dataRows the list of data rows (JsonObject), each containing candidate_id, embedding and candidate_name
     * @return the number of inserted rows
     */
    public int batchInsert(List<JsonObject> dataRows) {
        if (dataRows == null || dataRows.isEmpty()) {
            return 0;
        }

        InsertResp resp = milvusClient.insert(InsertReq.builder()
                .collectionName(COLLECTION_NAME)
                .data(dataRows)
                .build());

        log.info("Bulk vector insertion finished: count={}", resp.getInsertCnt());
        return (int) resp.getInsertCnt();
    }

    /**
     * Similarity search
     *
     * @param queryEmbedding the query vector (1024 dimensions)
     * @param topK           return the K most similar results
     * @return the list of search results (containing the candidateId and the similarity score)
     */
    public List<SearchResult> search(List<Float> queryEmbedding, int topK) {
        log.info("Running a vector search: topK={}", topK);

        FloatVec queryVector = new FloatVec(queryEmbedding);

        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(COLLECTION_NAME)
                .data(Collections.singletonList(queryVector))
                .topK(topK)
                .annsField("embedding")
                .outputFields(List.of("candidate_id", "candidate_name"))
                .build());

        List<SearchResult> results = new ArrayList<>();
        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

        if (searchResults != null && !searchResults.isEmpty()) {
            for (SearchResp.SearchResult hit : searchResults.get(0)) {
                SearchResult result = new SearchResult();
                result.setCandidateId(((Number) hit.getEntity().get("candidate_id")).longValue());
                result.setCandidateName((String) hit.getEntity().get("candidate_name"));
                result.setScore(hit.getScore());
                results.add(result);
            }
        }

        log.info("Vector search finished: resultCount={}", results.size());
        return results;
    }

    /**
     * Delete a candidate vector
     *
     * @param candidateId the candidate ID
     */
    public void deleteVector(Long candidateId) {
        log.info("Deleting the candidate vector: candidateId={}", candidateId);

        milvusClient.delete(DeleteReq.builder()
                .collectionName(COLLECTION_NAME)
                .ids(List.of(candidateId))
                .build());

        log.info("Candidate vector deleted successfully: candidateId={}", candidateId);
    }

    /**
     * Load the collection into memory (it must be loaded before searching)
     */
    private void loadCollection() {
        milvusClient.loadCollection(LoadCollectionReq.builder()
                .collectionName(COLLECTION_NAME)
                .build());
        log.info("Milvus collection loaded into memory: {}", COLLECTION_NAME);
    }

    /**
     * Vector search result
     */
    @lombok.Data
    public static class SearchResult {
        /** The candidate ID */
        private Long candidateId;
        /** The candidate name (redundant field) */
        private String candidateName;
        /** Similarity score (COSINE: 0~1, closer to 1 means more similar) */
        private float score;
    }
}
