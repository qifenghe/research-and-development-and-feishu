package com.lhr.rnd.service;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;

/** Conservative text pagination within the original cell/font/merge dimensions. */
final class TemplateText {
    record Capacity(int units,int lines) { Capacity lines(int count){return new Capacity(units,Math.max(0,count));} }
    record Part(String text,int end,int lines) {}
    static Capacity capacity(Sheet s,String address) {
        var ref=new CellReference(address);int r1=ref.getRow(),r2=r1,c1=ref.getCol(),c2=c1;
        for(var range:s.getMergedRegions())if(range.isInRange(r1,c1)){r1=range.getFirstRow();r2=range.getLastRow();c1=range.getFirstColumn();c2=range.getLastColumn();break;}
        var cell=s.getRow(r1).getCell(c1);var font=s.getWorkbook().getFontAt(cell.getCellStyle().getFontIndexAsInt());double points=font.getFontHeightInPoints();
        double width=0,height=0;for(int c=c1;c<=c2;c++)width+=s.getColumnWidth(c)/256.0*7;
        for(int r=r1;r<=r2;r++)height+=s.getRow(r)==null?s.getDefaultRowHeightInPoints():s.getRow(r).getHeightInPoints();
        return new Capacity(Math.max(4,(int)((width-8)/(points*96/72*.6))),cell.getCellStyle().getWrapText()?Math.max(1,(int)((height-3)/(points+2))):1);
    }
    static Part take(String text,int start,Capacity c) {
        if(start>=text.length()||c.lines()==0)return new Part("",start,0);
        int end=start,lines=1,units=0;
        while(end<text.length()) {
            int ch=text.codePointAt(end);int width=ch>255?2:1;
            if(ch=='\n') {if(lines>=c.lines()) {end++;break;}lines++;units=0;end++;continue;}
            if(units+width>c.units()){if(lines>=c.lines())break;lines++;units=0;}
            units+=width;end+=Character.charCount(ch);
        }
        return new Part(text.substring(start,end).strip(),end,lines);
    }
}
