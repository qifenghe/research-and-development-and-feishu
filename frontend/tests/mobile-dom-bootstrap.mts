class FakeNode {
  parentNode:FakeNode|null=null;
  childNodes:FakeNode[]=[];
  nodeType:number;
  nodeValue:string|null;
  constructor(nodeType:number,nodeValue:string|null=null){this.nodeType=nodeType;this.nodeValue=nodeValue;}
  get firstChild(){return this.childNodes[0]??null;}
  get nextSibling(){if(!this.parentNode)return null;return this.parentNode.childNodes[this.parentNode.childNodes.indexOf(this)+1]??null;}
  appendChild(child:FakeNode){return this.insertBefore(child,null);}
  insertBefore(child:FakeNode,anchor:FakeNode|null){if(child.parentNode)child.parentNode.removeChild(child);child.parentNode=this;const index=anchor?this.childNodes.indexOf(anchor):-1;if(index<0)this.childNodes.push(child);else this.childNodes.splice(index,0,child);if(this===(globalThis as any).document?.body)(globalThis as any).__mobileMountedTestDocumentAppends++;return child;}
  removeChild(child:FakeNode){const index=this.childNodes.indexOf(child);if(index>=0){this.childNodes.splice(index,1);child.parentNode=null;}return child;}
  get textContent(){return this.nodeValue??this.childNodes.map(child=>child.textContent).join("");}
  set textContent(value:string){this.childNodes=[];this.nodeValue=value;}
}

class FakeElement extends FakeNode {
  style:Record<string,string>={};
  className="";
  attributes=new Map<string,string>();
  tagName:string;
  constructor(tagName:string){super(1);this.tagName=tagName;}
  setAttribute(name:string,value:string){this.attributes.set(name,String(value));}
  removeAttribute(name:string){this.attributes.delete(name);}
  getAttribute(name:string){return this.attributes.get(name)??null;}
  hasAttribute(name:string){return this.attributes.has(name);}
  addEventListener(){}
  removeEventListener(){}
}

class FakeText extends FakeNode {constructor(value:string){super(3,value);}}
class FakeComment extends FakeNode {constructor(value:string){super(8,value);}}
class FakeDocument {
  body=new FakeElement("body");
  documentElement=new FakeElement("html");
  createElement(tagName:string){return new FakeElement(tagName);}
  createElementNS(_namespace:string,tagName:string){return new FakeElement(tagName);}
  createTextNode(value:string){return new FakeText(value);}
  createComment(value:string){return new FakeComment(value);}
  querySelector(){return null;}
}

const document=new FakeDocument();
Object.assign(globalThis,{document,history:{state:null,pushState(){},replaceState(){}},Node:FakeNode,Element:FakeElement,HTMLElement:FakeElement,SVGElement:FakeElement,Text:FakeText,Comment:FakeComment,__mobileMountedTestDocumentAppends:0});
Object.assign(globalThis.window??(globalThis.window={} as Window),{document,addEventListener(){},removeEventListener(){},clearTimeout,setTimeout});
