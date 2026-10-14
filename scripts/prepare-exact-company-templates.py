"""Sanitize supplied OOXML in place within ZIP parts; never rebuild workbook formatting.

Only business cell contents and non-hygiene pictures are removed. Styles, row/column
definitions, merges, page settings and hygiene picture anchors remain untouched.
"""
import sys, zipfile, xml.etree.ElementTree as ET, posixpath, copy
from pathlib import Path

NS='http://schemas.openxmlformats.org/spreadsheetml/2006/main'
N={'s':NS}
ET.register_namespace('', NS)
def coords(a):
    col=''.join(c for c in a if c.isalpha()); row=int(''.join(c for c in a if c.isdigit()))
    n=0
    for c in col:n=n*26+ord(c)-64
    return row,n
def dynamic(kind, sheet, a):
    r,c=coords(a)
    if kind=='pricing':
        return a in {'D3','D5','I5','D7','G7','J7','D8','G8','J8','D9','G9','F39','H39','F40','F41','F42','I42','F43'} or 12<=r<=38 and c>=3 or 47<=r<=51 and c>=2
    if sheet==2:
        return a in {'D3','D5','D7','G7','I7','D8','G8','D9','G9','I9'} or 12<=r<=13 and c>=3 or 16<=r<=19 and c>=3 or 20<=r<=24 and c>=8
    if sheet==1:
        return r==41 or r==46 and c>=3 or r in {54,61}
    if sheet==3:return 14<=r<=19 and c>=3 or a=='H20'
    if sheet==4:return r==12 or r in {16,17,18} and c>=3 or 22<=r<=50
    if sheet==5:return 12<=r<=34 and c>=2 or 41<=r<=46 and c>=2 or r==54
    if sheet==6:return r>=5 or a=='B3'
    return False
def prepare(source,dest,kind):
    with zipfile.ZipFile(source) as z:parts={i.filename:z.read(i.filename) for i in z.infolist()}
    shared=ET.fromstring(parts.get('xl/sharedStrings.xml',b'<sst xmlns="'+NS.encode()+b'"/>'))
    strings=list(shared)
    for name,data in list(parts.items()):
        if name.startswith('xl/worksheets/sheet') and name.endswith('.xml'):
            index=int(name.rsplit('sheet',1)[1].split('.')[0]); root=ET.fromstring(data)
            for cell in root.findall('.//s:c',N):
                if dynamic(kind,index,cell.attrib['r']):
                    for child in list(cell):
                        if child.tag in {f'{{{NS}}}v',f'{{{NS}}}f',f'{{{NS}}}is'}:cell.remove(child)
                    cell.attrib.pop('t',None)
                elif cell.attrib.get('t')=='s':
                    v=cell.find('s:v',N);si=strings[int(v.text)] if v is not None else None
                    if v is not None:cell.remove(v)
                    cell.set('t','inlineStr');is_=ET.SubElement(cell,f'{{{NS}}}is')
                    if si is not None:
                        for child in si:is_.append(copy.deepcopy(child))
            parts[name]=ET.tostring(root,encoding='utf-8',xml_declaration=True)
    # Leave only the hygiene pictures (row 17-25) on the first SOP worksheet.
    if kind=='sop':
        xdr='http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing'
        rel='http://schemas.openxmlformats.org/officeDocument/2006/relationships'
        kept_media=set()
        for name,data in list(parts.items()):
            if name.startswith('xl/drawings/drawing') and name.endswith('.xml'):
                root=ET.fromstring(data);used=set()
                for anchor in list(root):
                    row=anchor.find(f'{{{xdr}}}from/{{{xdr}}}row')
                    if name!='xl/drawings/drawing1.xml' or row is None or int(row.text)!=16:root.remove(anchor)
                for e in root.iter():
                    if f'{{{rel}}}embed' in e.attrib:used.add(e.attrib[f'{{{rel}}}embed'])
                parts[name]=ET.tostring(root,encoding='utf-8',xml_declaration=True)
                rn=posixpath.dirname(name)+'/_rels/'+posixpath.basename(name)+'.rels'
                if rn in parts:
                    rr=ET.fromstring(parts[rn])
                    for e in list(rr):
                        if e.attrib['Id'] not in used:rr.remove(e)
                        else:kept_media.add(posixpath.normpath(posixpath.join(posixpath.dirname(name),e.attrib['Target'])))
                    parts[rn]=ET.tostring(rr,encoding='utf-8',xml_declaration=True)
        for name in list(parts):
            if name.startswith('xl/media/') and name not in kept_media:parts.pop(name)
    parts['xl/sharedStrings.xml']=f'<sst xmlns="{NS}" count="0" uniqueCount="0"/>'.encode()
    # Cached formula values are sample data too; retain the actual formula only.
    for name,data in list(parts.items()):
        if name.startswith('xl/worksheets/sheet') and name.endswith('.xml'):
            root=ET.fromstring(data)
            for cell in root.findall('.//s:c',N):
                if cell.find('s:f',N) is not None:
                    v=cell.find('s:v',N)
                    if v is not None:cell.remove(v)
            parts[name]=ET.tostring(root,encoding='utf-8',xml_declaration=True)
    if 'xl/calcChain.xml' in parts:
        parts.pop('xl/calcChain.xml')
        rn='xl/_rels/workbook.xml.rels'; rr=ET.fromstring(parts[rn])
        for e in list(rr):
            if e.attrib.get('Type','').endswith('/calcChain'):rr.remove(e)
        parts[rn]=ET.tostring(rr,encoding='utf-8',xml_declaration=True)
    dest=Path(dest);dest.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in parts.items():z.writestr(name,data)
    print(dest)
if __name__=='__main__':prepare(*sys.argv[1:])
