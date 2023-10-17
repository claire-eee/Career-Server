package com.example.career.domain.community.Service;

import com.example.career.domain.community.Dto.response.ArticleCountByCategoryDto;
import com.example.career.domain.community.Dto.response.ArticleDto;
import com.example.career.domain.community.Dto.response.CommentDto;
import com.example.career.domain.community.Dto.response.RecommentDto;
import com.example.career.domain.community.Entity.Article;
import com.example.career.domain.community.Entity.Comment;
import com.example.career.domain.community.Entity.Heart;
import com.example.career.domain.community.Repository.ArticleRepository;

import com.example.career.domain.community.Repository.CommentRepository;
import com.example.career.domain.community.Repository.HeartRepository;
import com.example.career.domain.community.Repository.RecommentRepository;

import com.example.career.domain.user.Entity.User;
import com.example.career.domain.user.Repository.UserRepository;
import com.example.career.global.utils.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final CommentRepository commentRepository;
    private final RecommentRepository recommentRepository;
    private final UserRepository userRepository;

    private final HeartRepository heartRepository;

    private final S3Uploader s3Uploader;

    public List<ArticleDto> getAllArticles(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Article> result = articleRepository.findAll(pageable);
        List<Article> articles = result.getContent();

        // Transform Article to ArticleDto
        List<ArticleDto> articleDtos = articles.stream()
                .map(ArticleDto::from)
                .collect(Collectors.toList());

        return articleDtos;
    }

    public List<ArticleDto> getCategoryArticles(int categoryId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Article> result = articleRepository.findByCategoryId(categoryId, pageable);
        List<Article> articles = result.getContent();

        // Transform Article to ArticleDto
        List<ArticleDto> articleDtos = articles.stream()
                .map(ArticleDto::from)
                .collect(Collectors.toList());

        return articleDtos;
    }


    public Article addArticle(ArticleDto articleDto, Long userId) {
        // 유저 엔터티를 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        Article article = articleRepository.save(articleDto.toArticleEntity(user));
        return article;
    }

    public List<String> uploadImages(List<MultipartFile> MultipartFile) throws IOException {
        List<String> urlList = s3Uploader.upload(MultipartFile,"static/article");
        return urlList;
    }

    private void deleteUploadedImage(String url) {
        s3Uploader.deleteFile(url);
    }

    public void updateArticle(ArticleDto articleDto, List<String> newImgUrls, Long userId) throws Exception {
//        articleRepository.updateArticleTitleAndContent(userId, articleDto.getId(), articleDto.getTitle(), articleDto.getContent(), LocalDateTime.now());
        // 기존 Article 조회
        Article article = articleRepository.findById(articleDto.getId())
                .orElseThrow(() -> new Exception("Article not found"));

        // Article 정보 및 이미지 URL 업데이트
        article.setTitle(articleDto.getTitle());
        article.setContent(articleDto.getContent());
        article.setCategoryId(articleDto.getCategoryId());

        // 이미지 URL 업데이트
        List<String> currentImages = Arrays.asList(article.getImg1(), article.getImg2(), article.getImg3(), article.getImg4(), article.getImg5(), article.getImg6());
        // 이미지 업로드 한 것 삭제
        for (String url: articleDto.getRemovedImageUrls()) {
            if (currentImages.contains(url)) {
                deleteUploadedImage(url);
            }
        }

        currentImages = currentImages.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        currentImages.removeAll(articleDto.getRemovedImageUrls());

        currentImages.addAll(newImgUrls);
        article.setImg1(currentImages.size() > 0 ? currentImages.get(0) : null);
        article.setImg2(currentImages.size() > 1 ? currentImages.get(1) : null);
        article.setImg3(currentImages.size() > 2 ? currentImages.get(2) : null);
        article.setImg4(currentImages.size() > 3 ? currentImages.get(3) : null);
        article.setImg5(currentImages.size() > 4 ? currentImages.get(4) : null);
        article.setImg6(currentImages.size() > 5 ? currentImages.get(5) : null);

        article.setUpdatedAt(LocalDateTime.now());

        articleRepository.save(article);
    }

    @Transactional
    public void deleteArticleByUserIdAndId(Long id, Long userId) throws Exception {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new Exception("Article not found"));
        List<String> images = Arrays.asList(article.getImg1(), article.getImg2(), article.getImg3(), article.getImg4(), article.getImg5(), article.getImg6());
        images = images.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        for (String url: images) {
            s3Uploader.deleteFile(url);
        }
        articleRepository.deleteByIdAndUserId(id, userId);
    }


    public Map<String, Object> getArticleInDetail(Long id, Long userId) {
        Map<String, Object> details = new HashMap<>();

        // 게시글 가져오기
        Article article = articleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Article not found"));
        ArticleDto articleDto = ArticleDto.from(article);

        // 해당 사용자가 좋아요를 누른 게시글/댓글/대댓글의 ID들 가져오기
        List<Heart> likedArticles = heartRepository.findByUserIdAndType(userId, 0);
        List<Heart> likedComments = heartRepository.findByUserIdAndType(userId, 1);
        List<Heart> likedRecomments = heartRepository.findByUserIdAndType(userId, 2);

        // 게시글 isLiked 설정
        articleDto.setIsHeartClicked(likedArticles.stream().anyMatch(heart -> heart.getTypeId().equals(article.getId())));
        details.put("article", articleDto);

        // 해당 게시글의 댓글 가져오기
        List<Comment> comments = commentRepository.findByArticleIdWithRecomments(id);
        List<CommentDto> commentDtos = comments.stream().map(comment -> {
            CommentDto commentDto = CommentDto.from(comment);
            // 댓글 isLiked 설정
            commentDto.setIsHeartClicked(likedComments.stream().anyMatch(heart -> heart.getTypeId().equals(comment.getId())));
            for (RecommentDto recommentDto : commentDto.getRecomments()) {
                // 대댓글 isLiked 설정
                recommentDto.setIsHeartClicked(likedRecomments.stream().anyMatch(heart -> heart.getTypeId().equals(recommentDto.getId())));
            }
            return commentDto;
        }).collect(Collectors.toList());
        details.put("comments", commentDtos);

        return details;
    }

    public List<ArticleCountByCategoryDto> getCountByCategoryId() {
            List<ArticleCountByCategoryDto> articleCountByCategoryDtos = articleRepository.countArticlesByCategoryId();
            return articleCountByCategoryDtos;
    }
}