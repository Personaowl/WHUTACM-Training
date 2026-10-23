package com.whut.training.service;

import com.whut.training.domain.dto.UpsolvePlanRequest;
import com.whut.training.domain.dto.UpsolvePlanView;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.UpsolvePlanRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class UpsolvePlanService {
    private final UpsolvePlanRepository repository;
    public UpsolvePlanService(UpsolvePlanRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true)
    public Page list(long userId, boolean completed, int page, int size) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 50) throw new BusinessException(400,"分页参数无效");
        var counts = repository.counts(userId);
        return new Page(repository.list(userId,completed,size,(long)(page-1)*size), counts.pending(),counts.completed(),page,size,
                completed ? counts.completed() : counts.pending());
    }
    @Transactional
    public UpsolvePlanView save(long userId, Long id, UpsolvePlanRequest request) {
        if (id != null) require(userId,id);
        String link = UpsolveProblemLink.normalize(request.link());
        String title = clean(request.title());
        if (title == null) title = UpsolveProblemLink.fallbackTitle(link);
        try {
            if (id == null) id = repository.create(userId,title,link,UpsolveProblemLink.hash(link),clean(request.note()));
            else if (repository.update(userId,id,title,link,UpsolveProblemLink.hash(link),clean(request.note())) == 0) require(userId,id);
        } catch (DuplicateKeyException error) {
            throw new BusinessException(409,"这道题已在你的补题计划中，请在待完成或已完成中查看");
        }
        return require(userId,id);
    }
    @Transactional
    public UpsolvePlanView complete(long userId, long id, boolean completed) {
        require(userId,id);
        repository.complete(userId,id,completed);
        return require(userId,id);
    }
    public void delete(long userId, long id) {
        if (repository.delete(userId,id) == 0) throw new BusinessException(404,"补题记录不存在");
    }
    private UpsolvePlanView require(long userId,long id) {
        return repository.find(userId,id).orElseThrow(() -> new BusinessException(404,"补题记录不存在"));
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public record Page(List<UpsolvePlanView> items, long pendingCount, long completedCount, int page, int pageSize, long total) { }
}
