package com.whut.training.service;

import com.whut.training.domain.dto.CheckInResultResponse;
import com.whut.training.domain.entity.UserDailyStatus;
import com.whut.training.repository.DailyProblemRepository;
import com.whut.training.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Commits slot completion, daily score and total points together, without holding a connection during CF calls. */
@Service
public class DailyCheckInRecorder {
    private final DailyProblemRepository dailyProblemRepository;
    private final UserRepository userRepository;

    public DailyCheckInRecorder(DailyProblemRepository dailyProblemRepository, UserRepository userRepository) {
        this.dailyProblemRepository = dailyProblemRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CheckInResultResponse record(Long userId, LocalDate date, String slot, String problemKey,
                                        Long submissionId, String verdict, int score) {
        userRepository.lockForCheckIn(userId);
        int slotScore = dailyProblemRepository.findUserDailySlotStatus(userId, date, problemKey)
                .map(UserDailyStatus::score)
                .map(previous -> Math.max(previous, score))
                .orElse(score);
        dailyProblemRepository.saveUserDailySlotStatus(
                userId, date, slot, problemKey, submissionId, verdict, slotScore
        );
        int oldScore = dailyProblemRepository.findUserDailyStatus(userId, date)
                .map(UserDailyStatus::score).orElse(0);
        int nextScore = Math.max(oldScore, dailyProblemRepository.maxUserDailySlotScore(userId, date));
        dailyProblemRepository.upsertUserDailyStatus(userId, date, submissionId, verdict, nextScore);
        if (nextScore > oldScore) {
            userRepository.incrementTotalPoints(userId, nextScore - oldScore);
        }
        return new CheckInResultResponse(slot.toUpperCase(java.util.Locale.ROOT), true, submissionId, verdict, nextScore);
    }
}
