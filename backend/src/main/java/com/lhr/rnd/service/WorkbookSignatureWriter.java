package com.lhr.rnd.service;

import com.lhr.rnd.model.DocumentSignature;
import org.apache.poi.ss.usermodel.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** Adds a version-bound signature block without shifting any material/formula rows. */
public final class WorkbookSignatureWriter {
    private WorkbookSignatureWriter() {}
    public static byte[] stamp(byte[] content, DocumentSignature signature) {
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(content)); var out = new ByteArrayOutputStream()) {
            append(book, signature);
            book.write(out); return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("写入文件编制与审核信息失败", e); }
    }
    public static void append(Workbook book, DocumentSignature signature) {
        for (var sheet : book) {
            if (isCompanyLayout(sheet) && sheet.getMergedRegions().stream().anyMatch(r->r.formatAsString().equals("I12:I16"))) {
                TemplateWorkbook.pricingSignature(sheet,signature);
                sheet.getRow(6).getCell(6).setCellValue(signature.reviewed()?"受控":"待审核");
                continue;
            }
            if (isCompanyLayout(sheet)) {
                // Company renderer already reserves wide signature rows. Never append a second block
                // or alter the established one-page print area when a director reviews the file.
                // Pre-migration files may have only an original printed author/date. Never replace
                // those historical values with an invented or missing trusted compiler identity.
                if(signature.compiledBy()!=null&&!signature.compiledBy().isBlank())
                    sheet.getRow(8).getCell(3,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(signature.compilerLabel());
                if(signature.compiledAt()!=null&&!signature.compiledAt().isBlank())
                    sheet.getRow(8).getCell(6,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(signature.compiledDate());
                sheet.getRow(7).getCell(9,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(signature.reviewed() ? "已审核" : "待审核");
                sheet.getRow(6).getCell(6,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(signature.reviewed() ? "已审核" : "待审核");
                boolean reviewFooter=false;
                for(var row:sheet) if(row.getCell(0)!=null) {
                    var text=new DataFormatter().formatCellValue(row.getCell(0));
                    if(text.startsWith("编制人：")&&signature.compiledBy()!=null&&signature.compiledAt()!=null)
                        row.getCell(0).setCellValue("编制人："+signature.compilerLabel()+"    编制日期："+signature.compiledDate());
                    if(text.startsWith("审核人：")) {
                        row.getCell(0).setCellValue("审核人："+signature.reviewerLabel()+"    审核日期："+signature.reviewedDate());reviewFooter=true;
                    }
                }
                if(!reviewFooter) {
                    int rowIndex=sheet.getLastRowNum()+1;
                    var row=sheet.createRow(rowIndex);row.setHeightInPoints(42);
                    var style=book.createCellStyle();style.setWrapText(true);style.setVerticalAlignment(VerticalAlignment.CENTER);
                    var font=book.createFont();font.setFontName("宋体");font.setFontHeightInPoints((short)10);style.setFont(font);
                    var cell=row.createCell(0);cell.setCellStyle(style);cell.setCellValue("审核人："+signature.reviewerLabel()+"    审核日期："+signature.reviewedDate());
                    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIndex,rowIndex,0,9));
                    int index=book.getSheetIndex(sheet);
                    if(book.getPrintArea(index)!=null) {
                        var area=new org.apache.poi.ss.util.AreaReference(book.getPrintArea(index),org.apache.poi.ss.SpreadsheetVersion.EXCEL2007);
                        book.setPrintArea(index,area.getFirstCell().getCol(),area.getLastCell().getCol(),area.getFirstCell().getRow(),rowIndex);
                    }
                }
                continue;
            }
            int start = -1;
            for (var row : sheet) if (row.getCell(0) != null && "文件编制与审核".equals(new DataFormatter().formatCellValue(row.getCell(0)))) { start = row.getRowNum(); break; }
            if (start < 0) start = sheet.getLastRowNum() + 2;
            var style = book.createCellStyle(); style.setWrapText(true);
            var font = book.createFont(); font.setFontName("宋体"); font.setFontHeightInPoints((short)10); style.setFont(font);
            String[][] rows = {{"文件编制与审核", ""}, {"编制人", signature.compilerLabel()}, {"编制日期", signature.compiledDate()}, {"审核人", signature.reviewerLabel()}, {"审核日期", signature.reviewedDate()}};
            for (int i=0;i<rows.length;i++) {
                var row = sheet.getRow(start+i); if(row == null) row = sheet.createRow(start+i);
                row.setHeightInPoints(24);
                for(int c=0;c<2;c++){var cell=row.getCell(c); if(cell==null)cell=row.createCell(c);cell.setCellValue(rows[i][c]);cell.setCellStyle(style);}
                // Keep supplied company column widths unchanged. Merge only this newly appended block.
                int endColumn = "原料".equals(sheet.getSheetName()) ? 9 : 5;
                boolean merged = false;
                for (var region : sheet.getMergedRegions()) if(region.getFirstRow()==start+i && region.getFirstColumn()==1) { merged=true; break; }
                if(!merged) sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(start+i,start+i,1,endColumn));
            }
            if (book.getPrintArea(book.getSheetIndex(sheet)) != null) {
                var area = new org.apache.poi.ss.util.AreaReference(book.getPrintArea(book.getSheetIndex(sheet)), org.apache.poi.ss.SpreadsheetVersion.EXCEL2007);
                book.setPrintArea(book.getSheetIndex(sheet),area.getFirstCell().getCol(),area.getLastCell().getCol(),area.getFirstCell().getRow(),start+4);
                sheet.getPrintSetup().setFitHeight((short)0);
            }
        }
    }
    private static boolean isCompanyLayout(Sheet sheet) {
        if(!"原料".equals(sheet.getSheetName())||sheet.getRow(8)==null||sheet.getRow(10)==null)return false;
        var format=new DataFormatter();
        return "重量（kg）".equals(format.formatCellValue(sheet.getRow(10).getCell(5)))
                && format.formatCellValue(sheet.getRow(10).getCell(7)).startsWith("领料重量");
    }
}
