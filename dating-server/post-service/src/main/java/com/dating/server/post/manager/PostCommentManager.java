package com.dating.server.post.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.post.entity.PostComment;
import com.dating.server.post.mapper.PostCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PostCommentManager {

    private final PostCommentMapper postCommentMapper;

    /** 查询帖子的评论列表，按创建时间正序 */
    public List<PostComment> getByPostId(Long postId) {
        return postCommentMapper.selectList(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getPostId, postId)
                        .eq(PostComment::getStatus, "PUBLISHED")
                        .orderByAsc(PostComment::getCreatedAt));
    }

    public PostComment getById(Long id) {
        return postCommentMapper.selectById(id);
    }

    /** 评论数 */
    public long countByPostId(Long postId) {
        return postCommentMapper.selectCount(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getPostId, postId)
                        .eq(PostComment::getStatus, "PUBLISHED"));
    }

    public void insert(PostComment comment) {
        postCommentMapper.insert(comment);
    }

    /** 软删除评论 */
    public void softDelete(Long commentId) {
        PostComment update = new PostComment();
        update.setId(commentId);
        update.setStatus("DELETED");
        postCommentMapper.updateById(update);
    }
}
