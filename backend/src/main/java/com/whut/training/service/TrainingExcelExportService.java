package com.whut.training.service;

import com.whut.training.domain.dto.TrainingExportRequest;
import com.whut.training.service.exporting.TrainingExportDataLoader;
import com.whut.training.service.exporting.TrainingWorkbookWriter;
import org.springframework.stereotype.Service;

/** Coordinates export data preparation and workbook rendering, keeping the HTTP result contract. */
@Service
public class TrainingExcelExportService {
    private final TrainingExportDataLoader dataLoader;
    private final TrainingWorkbookWriter workbookWriter;

    public TrainingExcelExportService(TrainingExportDataLoader dataLoader, TrainingWorkbookWriter workbookWriter) {
        this.dataLoader = dataLoader;
        this.workbookWriter = workbookWriter;
    }

    public ExportResult export(TrainingExportRequest request) {
        var data = dataLoader.load(request);
        return new ExportResult(data.filename(), workbookWriter.write(data));
    }

    public record ExportResult(String filename, byte[] content) { }
}
