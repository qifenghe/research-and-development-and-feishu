package com.lhr.rnd.service;

import com.lhr.rnd.model.DocumentSignature;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkbookSignatureWriterTest {
    @Test void legacyCompanyApprovalRetainsOriginalCompilerAndDateAndAddsActualReview() throws Exception {
        byte[] original;
        try(var book=new XSSFWorkbook();var out=new java.io.ByteArrayOutputStream()) {
            var sheet=book.createSheet("原料");
            sheet.createRow(6).createCell(6).setCellValue("待审核");sheet.createRow(7).createCell(9).setCellValue("待审批");
            var header=sheet.createRow(8);header.createCell(0).setCellValue("编写人");header.createCell(3).setCellValue("历史编写甲");header.createCell(6).setCellValue("2026-09-20");
            var titles=sheet.createRow(10);titles.createCell(5).setCellValue("重量（kg）");titles.createCell(7).setCellValue("领料重量（kg)");
            sheet.createRow(11).createCell(5).setCellValue(100);sheet.createRow(52).createCell(0).setCellValue("机密说明原文");
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(52,52,0,9));book.setPrintArea(0,0,9,0,52);sheet.getPrintSetup().setFitHeight((short)1);
            book.write(out);original=out.toByteArray();
        }
        var signature=new DocumentSignature(null,null,"审核总监乙","2026-10-13T11:00:00");
        var stamped=WorkbookSignatureWriter.stamp(original,signature);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(stamped))) {
            var sheet=book.getSheetAt(0);
            assertThat(sheet.getRow(8).getCell(3).getStringCellValue()).isEqualTo("历史编写甲");
            assertThat(sheet.getRow(8).getCell(6).getStringCellValue()).isEqualTo("2026-09-20");
            var text=new StringBuilder();for(var row:sheet)for(var cell:row)text.append(cell.toString());
            assertThat(text.toString()).contains("审核总监乙","审核日期：2026-10-13","机密说明原文");
            assertThat(sheet.getRow(11).getCell(5).getNumericCellValue()).isEqualTo(100);
            assertThat(sheet.getLastRowNum()).isEqualTo(53);
            assertThat(book.getPrintArea(0)).contains("$54");
        }
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(WorkbookSignatureWriter.stamp(stamped,signature)))) {
            assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(53);
        }
    }
    @Test void companyReviewStampUsesExistingFooterWithoutChangingPaginationOrFormulaRows() throws Exception {
        var d=PricingBasisServiceTest.valid().withSignature(DocumentSignature.pending("内勤甲","2026-10-13T09:00:00"));
        var original=CompanyPricingRenderer.render(d,new PricingBasisCalculator().calculate(d),false);
        var signature=new DocumentSignature("内勤甲","2026-10-13T09:00:00","总监乙","2026-10-13T10:00:00");
        var stamped=WorkbookSignatureWriter.stamp(original,signature);
        try(var before=new XSSFWorkbook(new ByteArrayInputStream(original));var after=new XSSFWorkbook(new ByteArrayInputStream(stamped))) {
            var s=after.getSheetAt(0);
            assertThat(s.getLastRowNum()).isEqualTo(before.getSheetAt(0).getLastRowNum());
            assertThat(after.getPrintArea(0)).isEqualTo(before.getPrintArea(0));
            assertThat(s.getPrintSetup().getFitHeight()).isEqualTo((short)1);
            assertThat(s.getRow(11).getCell(7).getCellFormula()).isEqualTo("F12/G12");
            var text=new StringBuilder();for(var row:s)for(var cell:row)text.append(cell.toString());
            assertThat(text.toString()).contains("内勤甲","2026-10-13","总监乙","研发批准").doesNotContain("文件编制与审核","待审核");
        }
        try(var twice=new XSSFWorkbook(new ByteArrayInputStream(WorkbookSignatureWriter.stamp(stamped,signature)));var once=new XSSFWorkbook(new ByteArrayInputStream(stamped))) {
            assertThat(twice.getSheetAt(0).getLastRowNum()).isEqualTo(once.getSheetAt(0).getLastRowNum());
        }
    }
    @Test void genericReportStampIsIdempotentAndRetainsNumericCells() throws Exception {
        try(var book=new XSSFWorkbook()) {
            var sheet=book.createSheet("报表");sheet.createRow(0).createCell(0).setCellValue(178);
            var sig=DocumentSignature.pending("编制甲","2026-10-13T09:00:00");
            WorkbookSignatureWriter.append(book,sig);int last=sheet.getLastRowNum();WorkbookSignatureWriter.append(book,sig);
            assertThat(sheet.getLastRowNum()).isEqualTo(last);
            assertThat(sheet.getRow(0).getCell(0).getNumericCellValue()).isEqualTo(178);
            assertThat(sheet.getRow(last-1).getCell(1).getStringCellValue()).contains("待审核");
        }
    }
}
