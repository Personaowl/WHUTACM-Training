package com.whut.training.service;

import com.whut.training.domain.dto.ProblemListItemRequest;
import com.whut.training.domain.dto.ProblemListItemView;
import com.whut.training.domain.dto.ProblemListSaveRequest;
import com.whut.training.domain.dto.ProblemListSummary;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.ProblemListRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemListServiceTest {

    @Test
    void adminCanPublishAReadOnlySharedList() {
        ProblemListRepository repository = mock(ProblemListRepository.class);
        ProblemListService service = new ProblemListService(repository);
        User admin = user(2L, UserRole.ADMIN);
        ProblemListSummary summary = summary(10L, 2L, true, true);

        when(repository.createList(2L, "区间 DP", "专题训练", true)).thenReturn(10L);
        when(repository.findById(10L, 2L)).thenReturn(Optional.of(summary));
        when(repository.findItems(10L)).thenReturn(List.of());

        var result = service.create(admin, new ProblemListSaveRequest(" 区间 DP ", " 专题训练 ", true));

        assertThat(result.list().shared()).isTrue();
        assertThat(result.list().owner()).isTrue();
    }

    @Test
    void sharedListsAreReadableButOnlyTheirOwnerCanModifyThem() {
        ProblemListRepository repository = mock(ProblemListRepository.class);
        ProblemListService service = new ProblemListService(repository);
        User viewer = user(3L, UserRole.USER);
        ProblemListSummary shared = summary(10L, 2L, true, false);
        when(repository.findById(10L, 3L)).thenReturn(Optional.of(shared));
        when(repository.findItems(10L)).thenReturn(List.of());

        assertThat(service.get(viewer, 10L).list().shared()).isTrue();
        assertThatThrownBy(() -> service.delete(viewer, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void codeforcesLinksUseLocalProblemMetadataWhenAddingAnItem() {
        ProblemListRepository repository = mock(ProblemListRepository.class);
        ProblemListService service = new ProblemListService(repository);
        User owner = user(1L, UserRole.USER);
        ProblemListSummary summary = summary(10L, 1L, false, true);
        String link = "https://codeforces.com/problemset/problem/607/B";
        ProblemListItemView item = new ProblemListItemView(
                20L, 10L, "Zuma", link, "重做", "607-B", 1800, "dp", 0, 1L, null
        );

        when(repository.findById(10L, 1L)).thenReturn(Optional.of(summary));
        when(repository.findProblemMetadata("607-B")).thenReturn(Optional.of(
                new ProblemListRepository.ProblemMetadata("607-B", "Zuma", 1800, "dp", link)
        ));
        when(repository.itemLinkExists(10L, link, null)).thenReturn(false);
        when(repository.addItem(10L, "Zuma", link, "重做", "607-B", 1800, "dp", 1L)).thenReturn(20L);
        when(repository.findItem(10L, 20L)).thenReturn(Optional.of(item));

        var result = service.addItem(
                owner,
                10L,
                new ProblemListItemRequest(null, link, "重做", null, null, null)
        );

        assertThat(result.problemKey()).isEqualTo("607-B");
        assertThat(result.title()).isEqualTo("Zuma");
        verify(repository).addItem(10L, "Zuma", link, "重做", "607-B", 1800, "dp", 1L);
    }

    private static User user(Long id, UserRole role) {
        return new User(id, "user-" + id, null, "password", role);
    }

    private static ProblemListSummary summary(
            Long id,
            Long ownerUserId,
            boolean shared,
            boolean owner
    ) {
        return new ProblemListSummary(
                id, "区间 DP", "专题训练", ownerUserId, "owner", "Owner",
                shared, false, 0, owner, null, null
        );
    }
}
