package com.lhr.rnd.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import com.lhr.rnd.model.DocumentSignature;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFRelation;

/** Only replaces values at existing template coordinates; never formats or resizes. */
final class TemplateWorkbook {
    private TemplateWorkbook() {}
    static Cell put(Sheet s,String address,Object value) {
        var ref=new CellReference(address);var row=s.getRow(ref.getRow());
        if(row==null)throw new IllegalArgumentException("模板缺少行："+address);
        var cell=row.getCell(ref.getCol());if(cell==null)cell=row.createCell(ref.getCol());
        if(value==null)cell.setBlank();else if(value instanceof Number n)cell.setCellValue(n.doubleValue());else cell.setCellValue(value.toString());
        return cell;
    }
    static String text(String s){return s==null?"":s;}
    static XSSFSheet cloneSheet(XSSFWorkbook w,int index,String name) {
        var source=w.getSheetAt(index);var target=w.cloneSheet(index,name);
        // POI's normal clone explicitly drops page setup and does not clone print-area names.
        if(source.getCTWorksheet().isSetPageSetup()) {
            target.getCTWorksheet().setPageSetup((org.openxmlformats.schemas.spreadsheetml.x2006.main.CTPageSetup)source.getCTWorksheet().getPageSetup().copy());
            var setup=target.getCTWorksheet().getPageSetup();
            if(setup.isSetId()) {
                var part=source.getRelationById(setup.getId());
                if(part!=null)setup.setId(target.addRelation(null,XSSFRelation.PRINTER_SETTINGS,part).getRelationship().getId());
                else setup.unsetId();
            }
        }
        if(w.getPrintArea(index)!=null) {
            var area=new org.apache.poi.ss.util.AreaReference(w.getPrintArea(index),org.apache.poi.ss.SpreadsheetVersion.EXCEL2007);
            w.setPrintArea(w.getSheetIndex(target),area.getFirstCell().getCol(),area.getLastCell().getCol(),area.getFirstCell().getRow(),area.getLastCell().getRow());
        }
        if(source.getRepeatingRows()!=null)target.setRepeatingRows(source.getRepeatingRows());
        if(source.getRepeatingColumns()!=null)target.setRepeatingColumns(source.getRepeatingColumns());
        return target;
    }
    static void pricingSignature(Sheet s,DocumentSignature signature) {
        if(signature.compiledBy()!=null)put(s,"D9",signature.compilerLabel());
        if(signature.compiledAt()!=null)put(s,"G9",signature.compiledDate());
        // The original narrow merged J8:J9 is not wrapped: use the actual name (no
        // added role prefix) and compact date so both fit without changing its font.
        put(s,"J8",signature.reviewed()?signature.reviewedBy()+" "+signature.reviewedDate().replaceFirst("^20","").replace('-','.') : "待审核");
    }
}
