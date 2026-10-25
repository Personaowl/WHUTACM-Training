package com.whut.training.service;

import com.whut.training.service.impl.UserServiceImpl;
import com.whut.training.service.exporting.TrainingExportData;
import com.whut.training.service.exporting.TrainingExportDataLoader;
import com.whut.training.service.exporting.TrainingWorkbookWriter;
import com.whut.training.repository.DailyProblemRepository;
import com.whut.training.repository.PracticeDrawRepository;
import com.whut.training.repository.ProblemCatalogRepository;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureBoundaryTest {
    @Test void exportRenderingDoesNotDependOnRepositoriesExternalClientsOrLiveUsers() {
        assertTrue(Arrays.stream(TrainingExcelExportService.class.getDeclaredFields())
                .allMatch(field -> field.getType() == TrainingExportDataLoader.class || field.getType() == TrainingWorkbookWriter.class));
        assertTrue(Arrays.stream(TrainingWorkbookWriter.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getSimpleName().endsWith("Repository")
                        || field.getType() == CodeforcesProfileService.class));
        assertTrue(Arrays.stream(TrainingExportDataLoader.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().startsWith("org.apache.poi.")));
        assertTrue(Arrays.stream(TrainingExportData.Member.class.getRecordComponents())
                .noneMatch(field -> field.getName().matches("(?i).*password.*|.*token.*|.*avatar.*")));
    }
    @Test void siteAccountServiceDoesNotDependOnExternalPlatformClients() {
        assertTrue(Arrays.stream(UserServiceImpl.class.getDeclaredFields())
                .noneMatch(field -> field.getType() == CodeforcesApiService.class || field.getType() == AtCoderApiService.class));
        assertTrue(Arrays.stream(UserService.class.getDeclaredMethods()).noneMatch(method -> method.getName().contains("Binding")));
    }
    @Test void dailyAndPracticeHaveSeparatePersistenceAndUseCases() {
        assertTrue(Arrays.stream(DailyProblemService.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().contains("Practice")));
        assertTrue(Arrays.stream(DailyProblemRepository.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().contains("Practice") || method.getName().equals("upsertProblems")
                        || method.getName().equals("countProblems") || method.getName().equals("findRandomProblem")));
        assertTrue(Arrays.stream(PracticeService.class.getDeclaredFields())
                .noneMatch(field -> field.getType() == DailyProblemRepository.class || field.getType() == DailyProblemService.class));
        assertTrue(Arrays.stream(ProblemPoolService.class.getDeclaredFields())
                .noneMatch(field -> field.getType() == DailyProblemRepository.class || field.getType() == PracticeDrawRepository.class));
        assertTrue(Arrays.stream(ProblemCatalogRepository.class.getDeclaredFields())
                .noneMatch(field -> field.getType() == DailyProblemRepository.class));
    }
}
