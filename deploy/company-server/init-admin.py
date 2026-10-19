#!/usr/bin/env python3
import base64, hashlib, os, subprocess, uuid
from pathlib import Path
root=Path(__file__).resolve().parent
config=dict(line.split("=",1) for line in (root/".env").read_text().splitlines() if line and not line.startswith("#"))
password=config["ADMIN_INITIAL_PASSWORD"]
salt=os.urandom(16)
encoded=lambda b:base64.b64encode(b).decode()
hash_value="pbkdf2$120000$"+encoded(salt)+"$"+encoded(hashlib.pbkdf2_hmac("sha256",password.encode(),salt,120000,32))
user_id="USR-"+uuid.uuid4().hex[:27]
sql="INSERT INTO user_account (id,username,password_hash,name,feishu_user_id,role,department_name,status,created_at,updated_at) VALUES ('"+user_id+"','admin','"+hash_value+"','超级管理员',NULL,'SYSTEM_ADMIN','信息部','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT (username) DO NOTHING;"
compose=[os.environ["LONGHUI_COMPOSE_BIN"]] if os.environ.get("LONGHUI_COMPOSE_BIN") else ["docker","compose"]
subprocess.run(compose+["exec","-T","db","psql","-U","rnd_sample","-d","rnd_sample","-v","ON_ERROR_STOP=1"],input=sql,text=True,cwd=root,check=True)
print("Administrator initialized; existing admin password is never overwritten.")
