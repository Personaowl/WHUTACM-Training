package com.whut.training.service.binding;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.CodeforcesBindingStartRequest;
import com.whut.training.exception.BusinessException;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.repository.UserRepository;
import com.whut.training.service.AtCoderApiService;
import com.whut.training.service.CodeforcesApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContestAccountBindingServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private CodeforcesApiService codeforcesApiService;
    @Mock private AtCoderApiService atCoderApiService;
    @Mock private TimeProvider timeProvider;
    private ContestAccountBindingService bindingService;

    @BeforeEach void setUp() {
        bindingService = new ContestAccountBindingService(userRepository, codeforcesApiService, atCoderApiService, timeProvider);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    private User pendingCodeforces() {
        User user = new User(1L, "member", null, "hash", UserRole.USER);
        user.setCodeforcesHandle("previous-handle");
        user.setPendingCodeforcesHandle("tourist");
        user.setCodeforcesBindingStartedAtSeconds(1_000L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test void expiredCodeforcesProofClearsOnlyPendingStateWithoutCallingExternalApi() {
        User user = pendingCodeforces();
        when(timeProvider.nowEpochSecond()).thenReturn(1_121L);
        assertEquals(400, assertThrows(BusinessException.class, () -> bindingService.finishCodeforcesBinding(1L)).getCode());
        assertNull(user.getPendingCodeforcesHandle());
        assertEquals("previous-handle", user.getCodeforcesHandle());
        verify(userRepository).save(user);
        verifyNoInteractions(codeforcesApiService, atCoderApiService);
    }

    @Test void missingProofDoesNotChangeThePreviouslyVerifiedAccount() {
        User user = pendingCodeforces();
        when(timeProvider.nowEpochSecond()).thenReturn(1_050L);
        when(codeforcesApiService.hasOwnershipVerificationSubmission("tourist", 1_000L)).thenReturn(Optional.of(false));
        assertEquals(400, assertThrows(BusinessException.class, () -> bindingService.finishCodeforcesBinding(1L)).getCode());
        assertEquals("previous-handle", user.getCodeforcesHandle());
        assertEquals("tourist", user.getPendingCodeforcesHandle());
        verify(userRepository, never()).save(any());
    }

    @Test void externalFailureIsNotTreatedAsMissingProofOrSuccessfulBinding() {
        User user = pendingCodeforces();
        when(timeProvider.nowEpochSecond()).thenReturn(1_050L);
        when(codeforcesApiService.hasOwnershipVerificationSubmission("tourist", 1_000L)).thenReturn(Optional.empty());
        assertEquals(503, assertThrows(BusinessException.class, () -> bindingService.finishCodeforcesBinding(1L)).getCode());
        assertEquals("previous-handle", user.getCodeforcesHandle());
        verify(userRepository, never()).save(any());
    }

    @Test void validProofCannotStealAnotherMembersAccount() {
        pendingCodeforces();
        when(timeProvider.nowEpochSecond()).thenReturn(1_050L);
        when(codeforcesApiService.hasOwnershipVerificationSubmission("tourist", 1_000L)).thenReturn(Optional.of(true));
        when(userRepository.findByCodeforcesHandle("tourist")).thenReturn(Optional.of(new User(2L, "other", null, "hash", UserRole.USER)));
        assertEquals(400, assertThrows(BusinessException.class, () -> bindingService.finishCodeforcesBinding(1L)).getCode());
        verify(codeforcesApiService, never()).getUserInfo(any());
        verify(userRepository, never()).save(any());
    }

    @Test void expiredAtCoderProofClearsTheTokenWithoutFetchingTheProfile() {
        User user = new User(1L, "member", null, "hash", UserRole.USER);
        user.setAtcoderHandle("verified-old");
        user.setPendingAtcoderHandle("candidate");
        user.setAtcoderBindingToken("WHUT-ACM-TOKEN");
        user.setAtcoderBindingStartedAtSeconds(1_000L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(timeProvider.nowEpochSecond()).thenReturn(1_601L);
        assertEquals(400, assertThrows(BusinessException.class, () -> bindingService.finishAtCoderBinding(1L)).getCode());
        assertEquals("verified-old", user.getAtcoderHandle());
        assertNull(user.getPendingAtcoderHandle());
        assertNull(user.getAtcoderBindingToken());
        assertNull(user.getAtcoderBindingStartedAtSeconds());
        verifyNoInteractions(atCoderApiService);
    }

    @Test void nonexistentMemberDoesNotTriggerExternalRequests() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(BusinessException.class, () -> bindingService.finishAtCoderBinding(99L)).getCode());
        verifyNoInteractions(codeforcesApiService, atCoderApiService);
    }

    @Test
    void finishBindingStoresHandleAfterOwnershipSubmission() {
        User user = new User(1L, "site-user", null, "123456", UserRole.USER);
        user.setPendingCodeforcesHandle("tourist");
        user.setCodeforcesBindingStartedAtSeconds(1_000L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(timeProvider.nowEpochSecond()).thenReturn(1_050L);
        when(codeforcesApiService.hasOwnershipVerificationSubmission("tourist", 1_000L))
                .thenReturn(Optional.of(true));
        when(userRepository.findByCodeforcesHandle("tourist")).thenReturn(Optional.empty());
        when(codeforcesApiService.getUserInfo("tourist"))
                .thenReturn(Optional.of(new CodeforcesApiService.CodeforcesUserProfile(
                        3800, 4000, true, 1_050L, null
                )));

        User saved = bindingService.finishCodeforcesBinding(1L);

        assertEquals("tourist", saved.getCodeforcesHandle());
        assertEquals(3800, saved.getCodeforcesRating());
        assertNull(saved.getPendingCodeforcesHandle());
        assertNull(saved.getCodeforcesBindingStartedAtSeconds());
    }

    @Test
    void startBindingStoresPendingVerificationWindow() {
        User user = new User(1L, "site-user", null, "123456", UserRole.USER);
        CodeforcesBindingStartRequest request = new CodeforcesBindingStartRequest();
        request.setHandle(" tourist ");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByCodeforcesHandle("tourist")).thenReturn(Optional.empty());
        when(codeforcesApiService.getUserInfo("tourist"))
                .thenReturn(Optional.of(new CodeforcesApiService.CodeforcesUserProfile(
                        null, null, false, null, null
                )));
        when(timeProvider.nowEpochSecond()).thenReturn(2_000L);

        var response = bindingService.startCodeforcesBinding(1L, request);

        assertEquals("tourist", user.getPendingCodeforcesHandle());
        assertEquals(2_000L, user.getCodeforcesBindingStartedAtSeconds());
        assertEquals(2_120L, response.expiresAtSeconds());
    }

    @Test
    void finishAtCoderBindingVerifiesAffiliationAndClearsTemporaryToken() {
        User user = new User(1L, "site-user", null, "123456", UserRole.USER);
        user.setPendingAtcoderHandle("owl_atcoder");
        user.setAtcoderBindingToken("WHUT-ACM-7A2B9C1D");
        user.setAtcoderBindingStartedAtSeconds(3_000L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(timeProvider.nowEpochSecond()).thenReturn(3_120L);
        when(atCoderApiService.getPublicProfile("owl_atcoder")).thenReturn(Optional.of(
                new AtCoderApiService.AtCoderPublicProfile(
                        "owl_atcoder", "Wuhan · WHUT-ACM-7A2B9C1D",
                        "https://atcoder.jp/users/owl_atcoder"
                )
        ));
        when(userRepository.findByAtcoderHandle("owl_atcoder")).thenReturn(Optional.empty());

        User saved = bindingService.finishAtCoderBinding(1L);

        assertEquals("owl_atcoder", saved.getAtcoderHandle());
        assertEquals(3_120L, saved.getAtcoderVerifiedAtSeconds());
        assertNull(saved.getPendingAtcoderHandle());
        assertNull(saved.getAtcoderBindingToken());
        assertNull(saved.getAtcoderBindingStartedAtSeconds());
    }
}
