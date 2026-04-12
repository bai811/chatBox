package com.team.rag.util;

import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaccardSimilarity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BM25关键词检索工具：基于词频的精确检索，弥补向量检索专业术语、代码命令检索的不足
 * 与向量检索结合实现混合检索，提升团队知识库检索准确性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BM25Retriever {

    private final JaccardSimilarity jaccard = new JaccardSimilarity();

    // BM25返回前K条
    @Value("${rag.hybrid-retrieval.bm25-top-k}")
    private Integer bm25TopK;

    // 内存缓存所有已入库的文本片段（用于BM25检索）
    private final List<TextSegment> segmentCache = Collections.synchronizedList(new ArrayList<>());

    /**
     * 将文本片段添加到BM25缓存中
     * @param segments 文本片段列表
     */
    public void addSegments(List<TextSegment> segments) {
        segmentCache.addAll(segments);
        log.info("BM25缓存新增{}个片段，当前总计{}个", segments.size(), segmentCache.size());
    }

    /**
     * BM25关键词检索
     * @param query 用户查询词
     * @return 按相关性排序的文本片段列表
     */
    public List<TextSegment> retrieve(String query) {
        log.info("开始BM25关键词检索，查询词：{}，返回前{}条", query, bm25TopK);
        if (segmentCache.isEmpty()) {
            log.warn("BM25检索：缓存中无文本片段");
            return Collections.emptyList();
        }

        // 计算每个片段与查询词的BM25相似度
        Map<TextSegment, Double> scoreMap = new HashMap<>();
        for (TextSegment segment : segmentCache) {
            double score = calculateBM25Score(query, segment.text());
            if (score > 0) {
                scoreMap.put(segment, score);
            }
        }

        // 按相似度降序排序，取前K条
        List<TextSegment> result = scoreMap.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(bm25TopK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.info("BM25检索完成，匹配到{}条相关片段", result.size());
        return result;
    }

    /**
     * 计算BM25相似度（简化版，使用杰卡德相似度+词频加权）
     */
    private double calculateBM25Score(String query, String text) {
        Set<String> queryWords = splitToWords(query);
        Set<String> textWords = splitToWords(text);
        if (queryWords.isEmpty() || textWords.isEmpty()) {
            return 0.0;
        }

        double jaccardScore = jaccard.apply(query, text);
        // 词频加权：查询词在文本中出现的次数越多，得分越高
        int termFreq = 0;
        List<String> textWordList = Arrays.asList(text.replaceAll("[\\pP\\pS]", " ").toLowerCase().trim().split("\\s+"));
        for (String word : queryWords) {
            termFreq += Collections.frequency(textWordList, word);
        }
        return jaccardScore * (1 + Math.log1p(termFreq));
    }

    /**
     * 文本分词（简单实现）
     */
    private Set<String> splitToWords(String text) {
        String cleanText = text.replaceAll("[\\pP\\pS]", " ").toLowerCase().trim();
        return new HashSet<>(Arrays.asList(cleanText.split("\\s+")));
    }
}
