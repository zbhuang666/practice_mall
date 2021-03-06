package com.zhs.zhsmall.service.impl;

import com.alibaba.fastjson.JSON;
import com.zhs.zhsmall.domain.EsProduct;
import com.zhs.zhsmall.service.ZhsSearchService;
import com.zhs.zhsmall.utils.SearchConstant;
import com.zhs.zhsmall.vo.ESRequestParam;
import com.zhs.zhsmall.vo.ESResponseResult;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ZhsSearchServiceImpl implements ZhsSearchService {

    @Qualifier("client")
    @Autowired
    RestHighLevelClient client;

    @Override
    public ESResponseResult search(ESRequestParam param) {
        try {
            //1?????????????????????-??????????????????????????????
            SearchRequest searchRequest = startBuildRequestParam(param);
            //2??? ???????????????????????????
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            //3??????????????????
            ESResponseResult responseResult = startBuildResponseResult(searchResponse, param);
            return responseResult;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private ESResponseResult startBuildResponseResult(SearchResponse response, ESRequestParam param) {

        ESResponseResult result = new ESResponseResult();

        //1?????????????????????????????????
        SearchHits hits = response.getHits();

        List<EsProduct> esModels = new ArrayList<>();
        //2???????????????????????????
        if (hits.getHits() != null && hits.getHits().length > 0) {
            for (SearchHit hit : hits.getHits()) {
                String sourceAsString = hit.getSourceAsString();
                EsProduct esModel = JSON.parseObject(sourceAsString, EsProduct.class);

                //2.1 ????????????????????????????????????????????????????????????????????????
                if (!StringUtils.isEmpty(param.getKeyword())) {
                    //2.2 ??????????????????????????????
                    HighlightField name = hit.getHighlightFields().get("name");
                    //2.3 ??????name?????????????????????????????????(???????????????????????????????????????????????????????????????????????????????????????????????????name???????????????)
                    String nameValue = name!=null ? name.getFragments()[0].string() : esModel.getName();
                    esModel.setName(nameValue);
                }
                esModels.add(esModel);
            }
        }
        result.setProducts(esModels);

        //3???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        List<ESResponseResult.BrandVo> brandVos = new ArrayList<>();
        //????????????????????????
        ParsedLongTerms brandAgg = response.getAggregations().get("brand_agg");
        for (Terms.Bucket bucket : brandAgg.getBuckets()) {
            ESResponseResult.BrandVo brandVo = new ESResponseResult.BrandVo();

            //???????????????id
            long brandId = bucket.getKeyAsNumber().longValue();
            brandVo.setBrandId(brandId);

            //?????????????????????
            ParsedStringTerms brandNameAgg = bucket.getAggregations().get("brand_name_agg");
            String brandName = brandNameAgg.getBuckets().get(0).getKeyAsString();
            brandVo.setBrandName(brandName);

            //???????????????LOGO
            ParsedStringTerms brandImgAgg = bucket.getAggregations().get("brand_img_agg");
            String brandImg = brandImgAgg.getBuckets().get(0).getKeyAsString();
            brandVo.setBrandImg(brandImg);
            System.out.println("brandId:"+brandId+"brandName:"+brandName+"brandImg");
            brandVos.add(brandVo);
        }
        System.out.println("brandVos.size:"+brandVos.size());
        result.setBrands(brandVos);


        //4??????????????????????????????????????????
        //????????????????????????
        List<ESResponseResult.categoryVo> categoryVos = new ArrayList<>();

        ParsedLongTerms categoryAgg = response.getAggregations().get("category_agg");


        for (Terms.Bucket bucket : categoryAgg.getBuckets()) {
            ESResponseResult.categoryVo categoryVo = new ESResponseResult.categoryVo();
            //????????????id
            String keyAsString = bucket.getKeyAsString();
            categoryVo.setCategoryId(Long.parseLong(keyAsString));

            //???????????????
            ParsedStringTerms categoryNameAgg = bucket.getAggregations().get("category_name_agg");
            String categoryName = categoryNameAgg.getBuckets().get(0).getKeyAsString();
            categoryVo.setCategoryName(categoryName);
            categoryVos.add(categoryVo);
        }

        result.setCategorys(categoryVos);



        //5??????????????????????????????????????????
        List<ESResponseResult.AttrVo> attrVos = new ArrayList<>();
        //???????????????????????????
        ParsedNested attrsAgg = response.getAggregations().get("attr_agg");
        ParsedLongTerms attrIdAgg = attrsAgg.getAggregations().get("attr_id_agg");
        for (Terms.Bucket bucket : attrIdAgg.getBuckets()) {
            ESResponseResult.AttrVo attrVo = new ESResponseResult.AttrVo();
            //????????????ID???
            long attrId = bucket.getKeyAsNumber().longValue();
            attrVo.setAttrId(attrId);

            //?????????????????????
            ParsedStringTerms attrNameAgg = bucket.getAggregations().get("attr_name_agg");
            String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
            attrVo.setAttrName(attrName);

            //??????????????????
            ParsedStringTerms attrValueAgg = bucket.getAggregations().get("attr_value_agg");
            System.out.println("===1==="+attrValueAgg.getBuckets());

            for (Terms.Bucket b : attrValueAgg.getBuckets()) {
                String bb = b.getKeyAsString();
                System.out.println("bb:"+bb);
            }

            List<String> attrValues = attrValueAgg.getBuckets().stream().map(item -> item.getKeyAsString()).collect(Collectors.toList());
            attrVo.setAttrValue(attrValues);
            System.out.println("===2==="+attrValues);
            attrVos.add(attrVo);
        }

        result.setAttrs(attrVos);

        //6?????????????????????
        result.setPageNum(param.getPageNum());
        //??????????????????
        long total = hits.getTotalHits();
        result.setTotal(total);

        //???????????????
        int totalPages = (int) total % SearchConstant.PAGE_SIZE == 0 ?
                (int) total / SearchConstant.PAGE_SIZE : ((int) total / SearchConstant.PAGE_SIZE + 1);
        result.setTotalPages(totalPages);

        List<Integer> pageNavs = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            pageNavs.add(i);
        }
        result.setPageNavs(pageNavs);

        return result;
    }

    /**
     * {
     * "from": 0,
     * "size": 8,
     * "query": {
     * "bool": {
     * "must": [
     * {
     * "match": {
     * "name": {
     * "query": "??????"
     * }
     * }
     * }
     * ],
     * "filter": [
     * {
     * "term": {
     * "hasStock": {
     * "value": true
     * }
     * }
     * },
     * {
     * "range": {
     * "price": {
     * "from": "1",
     * "to": "5000"
     * }
     * }
     * }
     * ]
     * }
     * },
     * "sort": [
     * {
     * "salecount": {
     * "order": "asc"
     * }
     * }
     * ],
     * "aggregations": {
     * "brand_agg": {
     * "terms": {
     * "field": "brandId",
     * "size": 50
     * },
     * "aggregations": {
     * "brand_name_agg": {
     * "terms": {
     * "field": "brandName"
     * }
     * },
     * "brand_img_agg": {
     * "terms": {
     * "field": "brandImg"
     * }
     * }
     * }
     * },
     * "category_agg": {
     * "terms": {
     * "field": "categoryId",
     * "size": 50,
     * "min_doc_count": 1
     * },
     * "aggregations": {
     * "category_name_agg": {
     * "terms": {
     * "field": "categoryName"
     * }
     * }
     * }
     * },
     * "attr_agg": {
     * "nested": {
     * "path": "attrs"
     * },
     * "aggregations": {
     * "attr_id_agg": {
     * "terms": {
     * "field": "attrs.attrId"
     * },
     * "aggregations": {
     * "attr_name_agg": {
     * "terms": {
     * "field": "attrs.attrName"
     * }
     * },
     * "attr_value_agg": {
     * "terms": {
     * "field": "attrs.attrValue"
     * }
     * }
     * }
     * }
     * }
     * }
     * },
     * "highlight": {
     * "pre_tags": [
     * "<b style='color:red'>"
     * ],
     * "post_tags": [
     * "</b>"
     * ],
     * "fields": {
     * "name": {
     * <p>
     * }
     * }
     * }
     * }
     *
     * @param param
     * @return
     */
    private SearchRequest startBuildRequestParam(ESRequestParam param) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        // ????????????????????????
        if (!StringUtils.isEmpty(param.getKeyword())) {
            boolQueryBuilder.must(QueryBuilders.multiMatchQuery(param.getKeyword(), "name", "keywords", "subTitle"));
        }

        //2???????????????ID????????????
        if (null != param.getCategoryId()) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("categoryId", param.getCategoryId()));
        }

        //3???????????????ID????????????
        if (null != param.getBrandId() && param.getBrandId().size() > 0) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", param.getBrandId()));
        }

        //4?????????????????????????????????
        if (null != param.getAttrs() && param.getAttrs().size() > 0) {
            param.getAttrs().forEach(attr -> {
                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                //attrs=1_64G
                String[] s = attr.split("_");
                String attrId = s[0];
                String[] attrValues = s[1].split(":");
                boolQuery.must(QueryBuilders.termQuery("attrs.attrId", attrId));
                boolQuery.must(QueryBuilders.termsQuery("attrs.attrValue", attrValues));

                NestedQueryBuilder nestedQueryBuilder = QueryBuilders.nestedQuery("attrs", boolQuery, ScoreMode.Total);
                boolQueryBuilder.filter(nestedQueryBuilder);
            });
        }

        //5??????????????????
        if (null != param.getHasStock()) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("hasStock", param.getHasStock() == 1));
        }

        // 6.??????????????????
        if (!StringUtils.isEmpty(param.getPrice())) {
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("price");
            String[] price = param.getPrice().split("_");
            if (price.length == 2) {
                if (param.getPrice().startsWith("_")) {
                    rangeQueryBuilder.lte(price[1]);
                } else {
                    rangeQueryBuilder.gt(price[0]).lte(price[1]);
                }
            } else if (price.length == 1) {
                if (param.getPrice().endsWith("_")) {
                    rangeQueryBuilder.gte(price[0]);
                }
            }
            boolQueryBuilder.filter(rangeQueryBuilder);
        }
        searchSourceBuilder.query(boolQueryBuilder);

        //??????
        if(!StringUtils.isEmpty(param.getSort())) {
            String sort = param.getSort();
            String[] sortFields = sort.split("_");
            if(!StringUtils.isEmpty(sortFields[0])){
                SortOrder sortOrder = "asc".equalsIgnoreCase(sortFields[1]) ? SortOrder.ASC : SortOrder.DESC;
                searchSourceBuilder.sort(sortFields[0], sortOrder);
            }
        }

        //????????????
        searchSourceBuilder.from((param.getPageNum()-1)* SearchConstant.PAGE_SIZE);
        searchSourceBuilder.size(SearchConstant.PAGE_SIZE);

        //????????????
        if(!StringUtils.isEmpty(param.getKeyword())) {
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field(param.getKeyword());
            highlightBuilder.preTags("<b style='color:red'>");
            highlightBuilder.postTags("</b>");
            searchSourceBuilder.highlighter(highlightBuilder);
        }

        //1.????????????????????????
        TermsAggregationBuilder brand_agg = AggregationBuilders.terms("brand_agg");
        brand_agg.field("brandId").size(50);

        //1.1 ???????????????-??????????????????
        brand_agg.subAggregation(AggregationBuilders.terms("brand_name_agg").field("brandName").size(1));
        //1.2 ???????????????-??????????????????
        brand_agg.subAggregation(AggregationBuilders.terms("brand_img_agg").field("brandImg").size(1));

        searchSourceBuilder.aggregation(brand_agg);

        //2.??????????????????????????????
        TermsAggregationBuilder category_agg = AggregationBuilders.terms("category_agg");
        category_agg.field("categoryId").size(50);
        category_agg.subAggregation(AggregationBuilders.terms("category_name_agg").field("categoryName").size(1));
        searchSourceBuilder.aggregation(category_agg);

        //2.??????????????????????????????
        NestedAggregationBuilder attr_agg =AggregationBuilders.nested("attr_agg", "attrs");
        TermsAggregationBuilder attr_id_agg = AggregationBuilders.terms("attr_id_agg").field("attrs.attrId");
        TermsAggregationBuilder attr_name_agg = AggregationBuilders.terms("attr_name_agg").field("attrs.attrName").size(1);
        TermsAggregationBuilder attr_value_agg = AggregationBuilders.terms("attr_value_agg").field("attrs.attrValue").size(50);
        attr_agg.subAggregation(attr_id_agg);
        attr_id_agg.subAggregation(attr_name_agg);
        attr_id_agg.subAggregation(attr_value_agg);
        searchSourceBuilder.aggregation(attr_agg);

        System.out.println("?????????DSL?????? {}:"+ searchSourceBuilder.toString());
        SearchRequest searchRequest =new SearchRequest(new String[]{SearchConstant.INDEX_NAME}, searchSourceBuilder);

        return searchRequest;
    }
}
