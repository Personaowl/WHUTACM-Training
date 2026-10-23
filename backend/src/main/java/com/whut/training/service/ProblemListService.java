package com.whut.training.service;

import com.whut.training.domain.dto.ProblemListDetail;
import com.whut.training.domain.dto.ProblemListItemRequest;
import com.whut.training.domain.dto.ProblemListItemView;
import com.whut.training.domain.dto.ProblemListSaveRequest;
import com.whut.training.domain.dto.ProblemListSummary;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.ProblemListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 个人题单与成员协作共享题单业务。 */
@Service
public class ProblemListService implements ProblemListUseCase {

    private static final Pattern CF_PROBLEMSET = Pattern.compile(
            "codeforces\\.com/problemset/problem/(\\d+)/([A-Za-z0-9]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CF_CONTEST = Pattern.compile(
            "codeforces\\.com/(?:contest|gym)/(\\d+)/problem/([A-Za-z0-9]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final ProblemListRepository repository;

    public ProblemListService(ProblemListRepository repository) {
        this.repository = repository;
    }

    public List<ProblemListSummary> listVisible(User currentUser) {
        Long userId = requireUser(currentUser).getId();
        List<ProblemListSummary> lists = repository.findVisible(userId);
        if (lists.stream().anyMatch(ProblemListSummary::collaborative)) return lists;
        repository.ensureCommunity(userId);
        return repository.findVisible(userId);
    }

    public ProblemListDetail get(User currentUser, Long listId) {
        User user = requireUser(currentUser);
        ProblemListSummary list = requireVisible(listId, user);
        return new ProblemListDetail(list, repository.findItems(listId));
    }

    @Transactional
    public ProblemListDetail create(User currentUser, ProblemListSaveRequest request) {
        User user = requireUser(currentUser);
        boolean shared = requestedShared(request);
        if (shared && !isAdmin(user)) throw new BusinessException(403, "仅管理员可以发布共享题单");
        Long id = repository.createList(
                user.getId(),
                request.name().trim(),
                clean(request.description()),
                shared
        );
        if (id == null) throw new IllegalStateException("problem list id was not generated");
        return get(user, id);
    }

    @Transactional
    public ProblemListDetail update(User currentUser, Long listId, ProblemListSaveRequest request) {
        User user = requireUser(currentUser);
        repository.lockList(listId);
        ProblemListSummary existing = requireOwner(listId, user);
        boolean shared = requestedShared(request);
        if (shared && !existing.shared() && !isAdmin(user)) throw new BusinessException(403, "仅管理员可以发布共享题单");
        if (existing.shared() && !shared && repository.hasOtherContributors(listId, user.getId())) {
            throw new BusinessException(409, "该共享题单已有其他成员贡献，不能改为私有");
        }
        int updated = repository.updateList(
                listId,
                user.getId(),
                request.name().trim(),
                clean(request.description()),
                shared
        );
        if (updated == 0) throw new BusinessException(404, "problem list not found");
        return get(user, listId);
    }

    @Transactional
    public void delete(User currentUser, Long listId) {
        User user = requireUser(currentUser);
        repository.lockList(listId);
        requireOwner(listId, user);
        if (repository.deleteList(listId, user.getId()) == 0) {
            throw new BusinessException(404, "problem list not found");
        }
    }

    @Transactional
    public ProblemListItemView addItem(User currentUser, Long listId, ProblemListItemRequest request) {
        User user = requireUser(currentUser);
        repository.lockList(listId);
        ProblemListSummary list = requireVisible(listId, user);
        if (!list.owner() && !list.collaborative()) throw new BusinessException(403, "该题单仅供阅读");
        ResolvedProblem problem = resolveProblem(request);
        if (repository.itemLinkExists(listId, problem.link(), null)) {
            throw new BusinessException(409, "该题目已经在当前题单中");
        }
        Long itemId = repository.addItem(
                listId,
                problem.title(),
                problem.link(),
                problem.note(),
                problem.problemKey(),
                problem.rating(),
                problem.tags(),
                user.getId()
        );
        return repository.findItem(listId, itemId)
                .orElseThrow(() -> new IllegalStateException("problem list item was not created"));
    }

    @Transactional
    public ProblemListItemView updateItem(
            User currentUser,
            Long listId,
            Long itemId,
            ProblemListItemRequest request
    ) {
        User user = requireUser(currentUser);
        requireItemEditor(listId, itemId, user);
        ResolvedProblem problem = resolveProblem(request);
        if (repository.itemLinkExists(listId, problem.link(), itemId)) {
            throw new BusinessException(409, "该题目已经在当前题单中");
        }
        int updated = repository.updateItem(
                itemId,
                listId,
                problem.title(),
                problem.link(),
                problem.note(),
                problem.problemKey(),
                problem.rating(),
                problem.tags()
        );
        if (updated == 0) throw new BusinessException(404, "problem list item not found");
        return repository.findItem(listId, itemId)
                .orElseThrow(() -> new BusinessException(404, "problem list item not found"));
    }

    @Transactional
    public void deleteItem(User currentUser, Long listId, Long itemId) {
        User user = requireUser(currentUser);
        requireItemEditor(listId, itemId, user);
        if (repository.deleteItem(itemId, listId) == 0) {
            throw new BusinessException(404, "problem list item not found");
        }
    }

    private ResolvedProblem resolveProblem(ProblemListItemRequest request) {
        String link = requireHttpUrl(request.link());
        String problemKey = clean(request.problemKey());
        if (problemKey == null) problemKey = parseCodeforcesProblemKey(link);
        final String resolvedKey = problemKey;
        ProblemListRepository.ProblemMetadata metadata = repository.findProblemMetadata(resolvedKey).orElse(null);

        String title = clean(request.title());
        if (title == null && metadata != null) title = metadata.name();
        if (title == null) {
            throw new BusinessException(400, "本地题库无法识别该链接，请填写题目标题");
        }
        Integer rating = request.rating() != null
                ? request.rating()
                : metadata == null ? null : metadata.rating();
        if (rating != null && (rating < 0 || rating > 5000)) {
            throw new BusinessException(400, "rating must be between 0 and 5000");
        }
        String tags = normalizeTags(request.tags() != null
                ? request.tags()
                : metadata == null ? null : metadata.tags());
        return new ResolvedProblem(title, link, clean(request.note()), resolvedKey, rating, tags);
    }

    private ProblemListSummary requireVisible(Long listId, User user) {
        ProblemListSummary list = repository.findById(listId, user.getId())
                .orElseThrow(() -> new BusinessException(404, "problem list not found"));
        if (!list.owner() && !list.shared()) {
            throw new BusinessException(403, "problem list is private");
        }
        return list;
    }

    private ProblemListSummary requireOwner(Long listId, User user) {
        ProblemListSummary list = repository.findById(listId, user.getId())
                .orElseThrow(() -> new BusinessException(404, "problem list not found"));
        if (list.collaborative()) throw new BusinessException(403, "公共题单不能修改或删除");
        if (!list.owner()) throw new BusinessException(403, "only the owner can modify this problem list");
        return list;
    }

    private static boolean requestedShared(ProblemListSaveRequest request) {
        return Boolean.TRUE.equals(request.shared());
    }

    private void requireItemEditor(Long listId, Long itemId, User user) {
        repository.lockList(listId);
        ProblemListSummary list = requireVisible(listId, user);
        ProblemListItemView item = repository.findItem(listId, itemId)
                .orElseThrow(() -> new BusinessException(404, "problem list item not found"));
        boolean allowed = list.collaborative()
                ? isAdmin(user) || user.getId().equals(item.addedBy())
                : list.owner();
        if (!allowed) {
            throw new BusinessException(403, "只能修改自己添加的题目");
        }
    }

    private static boolean isAdmin(User user) {
        return user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN;
    }

    private static User requireUser(User user) {
        if (user == null || user.getId() == null) throw new BusinessException(401, "unauthorized");
        return user;
    }

    private static String requireHttpUrl(String value) {
        String cleaned = clean(value);
        try {
            URI uri = URI.create(cleaned == null ? "" : cleaned);
            String scheme = uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && uri.getUserInfo() == null) {
                return uri.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // 统一在下方返回业务错误。
        }
        throw new BusinessException(400, "题目链接必须是有效的 http/https 地址");
    }

    private static String parseCodeforcesProblemKey(String link) {
        for (Pattern pattern : List.of(CF_PROBLEMSET, CF_CONTEST)) {
            Matcher matcher = pattern.matcher(link);
            if (matcher.find()) {
                return matcher.group(1) + "-" + matcher.group(2).toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static String normalizeTags(String raw) {
        String cleaned = clean(raw);
        if (cleaned == null) return null;
        String normalized = Arrays.stream(cleaned.split("[,，]"))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return normalized.isBlank() ? null : normalized;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private record ResolvedProblem(
            String title,
            String link,
            String note,
            String problemKey,
            Integer rating,
            String tags
    ) {
    }
}
