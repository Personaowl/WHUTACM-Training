package com.whut.training.service;

import com.whut.training.aspect.annotation.ServiceLog;
import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.PracticeDrawResponse;
import com.whut.training.domain.dto.PracticeHistoryItem;
import com.whut.training.domain.dto.ProblemView;
import com.whut.training.domain.entity.CfProblem;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.entity.UserPracticeDraw;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.PracticeDrawRepository;
import com.whut.training.repository.ProblemCatalogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

/** Practice selection and personal history, independent of daily slots and scoring. */
@Service
@ServiceLog
public class PracticeService {
    private final ProblemCatalogRepository catalogRepository;
    private final PracticeDrawRepository practiceDrawRepository;
    private final ProblemPoolService problemPoolService;
    private final TimeProvider timeProvider;
    private final int defaultMinRating;
    private final int defaultMaxRating;

    public PracticeService(ProblemCatalogRepository catalogRepository, PracticeDrawRepository practiceDrawRepository,
                           ProblemPoolService problemPoolService, TimeProvider timeProvider,
                           @Value("${app.daily-problem.min-rating:1200}") int defaultMinRating,
                           @Value("${app.daily-problem.max-rating:2000}") int defaultMaxRating) {
        this.catalogRepository = catalogRepository;
        this.practiceDrawRepository = practiceDrawRepository;
        this.problemPoolService = problemPoolService;
        this.timeProvider = timeProvider;
        this.defaultMinRating = defaultMinRating;
        this.defaultMaxRating = defaultMaxRating;
    }

    /**
     * 按难度范围抽取练习题。
     *
     * @param user      当前用户。
     * @param minRating 最小 rating。
     * @param maxRating 最大 rating。
     * @return 抽题响应。
     */
    public PracticeDrawResponse drawPracticeProblem(User user, Integer minRating, Integer maxRating, List<String> tags) {
        problemPoolService.ensureAvailable();
        int resolvedMinRating = minRating == null ? defaultMinRating : minRating;
        int resolvedMaxRating = maxRating == null ? defaultMaxRating : maxRating;
        if (resolvedMinRating > resolvedMaxRating) {
            throw new BusinessException(400, "invalid rating range");
        }

        List<String> normalizedTags = tags == null ? List.of() : tags.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .map(tag -> tag.toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
        if (normalizedTags.size() > 5 || normalizedTags.stream().anyMatch(tag -> tag.length() > 64)) {
            throw new BusinessException(400, "标签选择无效，最多选择 5 个标签");
        }

        CfProblem problem = catalogRepository.findRandomProblem(
                        resolvedMinRating,
                        resolvedMaxRating,
                        normalizedTags
                )
                .orElseThrow(() -> new BusinessException(404, "没有符合当前难度和标签条件的题目"));
        UserPracticeDraw draw = practiceDrawRepository.insertPracticeDraw(user.getId(), timeProvider.today(), problem);
        return new PracticeDrawResponse(
                draw.id(),
                new ProblemView(
                        "PRACTICE",
                        draw.drawDate().toString(),
                        draw.problemKey(),
                        draw.contestId(),
                        draw.problemIndex(),
                        draw.name(),
                        draw.rating(),
                        draw.tags(),
                        draw.sourceUrl()
                )
        );
    }

    public List<String> getAvailablePracticeTags() {
        return catalogRepository.findAvailableProblemTags();
    }

    public List<PracticeHistoryItem> getPracticeHistory(User user, int limit) {
        List<UserPracticeDraw> draws = practiceDrawRepository.findPracticeDrawsByUserId(user.getId(), Math.max(1, Math.min(limit, 100)));
        return draws.stream()
                .map(d -> new PracticeHistoryItem(
                        d.id(),
                        d.drawDate().toString(),
                        d.problemKey(),
                        d.contestId(),
                        d.problemIndex(),
                        d.name(),
                        d.rating(),
                        d.tags(),
                        d.sourceUrl(),
                        null, // drawnAt not stored in current entity
                        d.submissionId(),
                        d.verdict()
                ))
                .toList();
    }

    public boolean deletePracticeDraw(User user, Long drawId) {
        return practiceDrawRepository.deletePracticeDraw(drawId, user.getId());
    }

}
