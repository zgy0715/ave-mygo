package com.avemygo.comment;

import com.avemygo.comment.CommentDtos.CommentView;
import com.avemygo.common.BizException;
import com.avemygo.song.Song;
import com.avemygo.song.SongRepository;
import com.avemygo.user.User;
import com.avemygo.user.UserRepository;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 歌曲评论业务。
 * 规则：内容 1-500 字；只能删自己的（ADMIN 例外）；歌曲必须存在且上架；软删除。
 * 列表批量取昵称，避免 N+1；own 字段交给前端决定是否显示删除按钮（后端仍二次校验）。
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CommentRepository commentRepository;
    private final SongRepository songRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<CommentView> list(Long songId, Long viewerId, boolean isAdmin, int page, int pageSize) {
        ensureSong(songId);
        Page<Comment> comments = commentRepository.findBySongIdOrderByIdDesc(songId,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE)));

        Map<Long, String> names = userRepository.findAllById(
                        comments.map(Comment::getUserId).stream().distinct().toList())
                .stream().collect(Collectors.toMap(User::getId,
                        u -> u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname()));

        return comments.map(c -> new CommentView(c.getId(), c.getUserId(),
                names.getOrDefault(c.getUserId(), "已注销用户"),
                c.getContent(), c.getCreatedAt(),
                isAdmin || c.getUserId().equals(viewerId)));
    }

    @Transactional
    public CommentView create(Long userId, Long songId, String content) {
        ensureSong(songId);
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) throw new BizException(40001, "评论内容不能为空");
        if (text.length() > 500) throw new BizException(40001, "评论最多 500 字");

        Comment c = new Comment();
        c.setSongId(songId);
        c.setUserId(userId);
        c.setContent(text);
        c = commentRepository.save(c);

        String nickname = userRepository.findById(userId)
                .map(u -> u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname())
                .orElse("我");
        return new CommentView(c.getId(), userId, nickname, c.getContent(), c.getCreatedAt(), true);
    }

    @Transactional
    public void delete(Long userId, boolean isAdmin, Long commentId) {
        Comment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new BizException(40401, "评论不存在"));
        if (!isAdmin && !c.getUserId().equals(userId)) {
            throw new BizException(40301, "只能删除自己的评论");
        }
        c.setIsDeleted((byte) 1);
        commentRepository.save(c);
    }

    private void ensureSong(Long songId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new BizException(40401, "歌曲不存在或已下架"));
        if (song.getStatus() == null || song.getStatus() != 1) {
            throw new BizException(40401, "歌曲不存在或已下架");
        }
    }
}
