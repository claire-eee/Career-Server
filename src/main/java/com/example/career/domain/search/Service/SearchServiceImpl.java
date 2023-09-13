package com.example.career.domain.search.Service;

import com.example.career.domain.community.Entity.Article;
import com.example.career.domain.community.Entity.Comment;
import com.example.career.domain.community.Entity.Recomment;
import com.example.career.domain.community.Repository.ArticleRepository;
import com.example.career.domain.community.Repository.CommentRepository;
import com.example.career.domain.community.Repository.RecommentRepository;
import com.example.career.domain.search.Dto.CommunitySearchRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService{
    private final ArticleRepository articleRepository;
    private final CommentRepository commentRepository;
    private final RecommentRepository recommentRepository;
    @Override
    public List<CommunitySearchRespDto> getArticlesByKeyWord(String keyWord) {
        List<Article> articles = articleRepository.findAllByTitleContainingOrContentContaining(keyWord, keyWord);
        List<Long> comments = commentRepository.findArticleIdsByContentContaining(keyWord);
        List<Long> recomms = recommentRepository.findArticleIdsByContentContaining(keyWord);
        int a = articles.size();
        int b= comments.size();
        int c= recomms.size();
        // Comment 및 Recomment의 Article 정보를 가져와서 articles에 추가
        for (Long articleId : comments) {
            Article article = articleRepository.findById(articleId).orElse(null);
            if (article != null && !articles.contains(article)) {
                articles.add(article);
            }
        }

        for (Long articleId : recomms) {
            Article article = articleRepository.findById(articleId).orElse(null);
            if (article != null && !articles.contains(article)) {
                articles.add(article);
            }
        }
        System.out.println(a+","+b+","+c);
        List<CommunitySearchRespDto> communitySearchRespDtos = articles.stream().map(Article::toDto).collect(Collectors.toList());
        return communitySearchRespDtos;
    }
}
