#!/usr/bin/env python3
import os, secrets
from pathlib import Path
root=Path(__file__).resolve().parent
os.umask(0o077)
env=root/".env"
if not env.exists():
    password=secrets.token_urlsafe(18)
    env.write_text("DB_PASSWORD="+secrets.token_hex(24)+"\nSESSION_SECRET="+secrets.token_hex(48)+"\nADMIN_INITIAL_PASSWORD="+password+"\n")
    (root/"credentials.txt").write_text("管理员账号：admin\n初始密码："+password+"\n首次登录后请修改密码；不要将此文件发到聊天或公开分享。\n")
    print("Generated .env and credentials.txt (private permissions).")
else:
    print("Existing .env preserved.")
