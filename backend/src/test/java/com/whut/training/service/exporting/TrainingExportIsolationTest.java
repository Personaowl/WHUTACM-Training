package com.whut.training.service.exporting;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.TrainingExportRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.MemberType;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.repository.AtCoderTrackingRepository;
import com.whut.training.repository.CodeforcesRatingHistoryRepository;
import com.whut.training.repository.TrainingDashboardRepository;
import com.whut.training.repository.UserRepository;
import com.whut.training.service.CodeforcesProfileService;
import com.whut.training.service.TrainingExcelExportService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingExportIsolationTest {
    private final TrainingExportRequest request = new TrainingExportRequest("WEEK", true, false, false);
    private final LocalDate date = LocalDate.of(2026, 10, 25);

    @Test void preparedMemberSnapshotCannotChangeOrCarryCredentialsIntoTheRenderer() {
        UserRepository users = mock(UserRepository.class);
        TimeProvider time = mock(TimeProvider.class);
        var member = new User(7L, "owl", null, "password-hash", UserRole.USER);
        member.setDisplayName("Original");
        member.setCodeforcesRating(1961);
        when(users.findByMemberType(MemberType.ACTIVE_TEAM)).thenReturn(List.of(member));
        when(time.today()).thenReturn(date);
        var loader = new TrainingExportDataLoader(users, mock(TrainingDashboardRepository.class),
                mock(CodeforcesRatingHistoryRepository.class), mock(CodeforcesProfileService.class),
                mock(AtCoderTrackingRepository.class), time);
        var data = loader.load(request);
        member.setDisplayName("Changed");
        member.setCodeforcesRating(0);
        assertThat(data.members().get(0).displayName()).isEqualTo("Original");
        assertThat(data.members().get(0).codeforcesRating()).isEqualTo(1961);
        assertThat(java.util.Arrays.stream(TrainingExportData.Member.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("id", "username", "displayName", "codeforcesHandle", "codeforcesRating");
        assertThatThrownBy(() -> data.members().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> data.dailyRows().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void oneWriterCanRenderConcurrentExportsWithoutSharingWorkbookState() throws Exception {
        var members = new java.util.ArrayList<>(List.of(new TrainingExportData.Member(7L, "owl", "Name", null, null)));
        var data = new TrainingExportData(request, date.minusDays(6), date, "local.xlsx", members,
                List.of(), List.of(), List.of());
        members.clear();
        assertThat(data.members()).hasSize(1);
        var writer = new TrainingWorkbookWriter();
        var workers = Executors.newFixedThreadPool(4);
        try {
            var results = new java.util.ArrayList<java.util.concurrent.Future<byte[]>>();
            for (int i = 0; i < 8; i++) results.add(workers.submit(() -> writer.write(data)));
            for (var result : results) {
                try (var book = new XSSFWorkbook(new ByteArrayInputStream(result.get(10, TimeUnit.SECONDS)))) {
                    assertThat(book.getNumberOfSheets()).isEqualTo(2);
                    assertThat(book.getSheetAt(0).getRow(5).getCell(0).getStringCellValue()).isEqualTo("owl");
                    assertThat(book.getNumCellStyles()).isLessThan(25);
                }
            }
        } finally { workers.shutdownNow(); }
    }

    @Test void facadePassesOnlyPreparedDataToTheWriterAndKeepsTheResultContract() {
        var loader = mock(TrainingExportDataLoader.class);
        var writer = mock(TrainingWorkbookWriter.class);
        var data = new TrainingExportData(request, date.minusDays(6), date, "export.xlsx", List.of(), List.of(), List.of(), List.of());
        byte[] content = {80, 75, 3, 4};
        when(loader.load(request)).thenReturn(data);
        when(writer.write(data)).thenReturn(content);
        var result = new TrainingExcelExportService(loader, writer).export(request);
        assertThat(result.filename()).isEqualTo("export.xlsx");
        assertThat(result.content()).isSameAs(content);
        var order = inOrder(loader, writer);
        order.verify(loader).load(request);
        order.verify(writer).write(data);
    }
}
