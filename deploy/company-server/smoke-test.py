#!/usr/bin/env python3
import json, urllib.request, urllib.error
from pathlib import Path
root=Path(__file__).resolve().parent
config=dict(line.split("=",1) for line in (root/".env").read_text().splitlines() if line and not line.startswith("#"))
base="http://127.0.0.1:"+config.get("HTTP_PORT","80")
def request(path,body=None,token=None):
    headers={"Content-Type":"application/json"}
    if token:headers["Authorization"]="Bearer "+token
    r=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
    try:
        with urllib.request.urlopen(r,timeout=20) as response:return response.status,response.read()
    except urllib.error.HTTPError as e:return e.code,e.read()
for path in ["/admin/login","/m/login"]:
    status,body=request(path);assert status==200 and b"<html" in body.lower(),(path,status)
    print(path+": HTTP 200")
status,body=request("/api/v1/auth/login",{"username":"admin","password":config["ADMIN_INITIAL_PASSWORD"]})
data=json.loads(body);assert status==200 and data["code"]=="0",(status,data.get("code"))
token=data["data"]["accessToken"]
assert data["data"]["user"]["role"]=="SYSTEM_ADMIN"
print("Administrator password login: PASS")
for path in ["/api/v1/session/me","/api/v1/materials","/api/v1/settings/users"]:
    status,body=request(path,token=token)
    data=json.loads(body);assert status==200 and data["code"]=="0",(path,status,data.get("code"))
    print(path+": authenticated PASS")
status,body=request("/api/v1/auth/login",{"username":"rnd_assistant","password":"123456"})
assert json.loads(body)["code"]=="AUTH_CREDENTIAL_INVALID"
print("Default demo account rejected: PASS")
