#!/usr/bin/env python3
"""
测试 ChronoSync API 备忘录接口
用于检测后端返回的备忘录数据

使用方法:
    python test_memo_api.py [用户名] [密码]
    
如果没有提供参数，会使用默认测试账号
"""

import requests
import json
import sys

BASE_URL = "http://115.190.155.26:8000"

def login(username: str, password: str) -> str:
    """登录获取 token"""
    url = f"{BASE_URL}/api/v1/auth/login"
    headers = {"Content-Type": "application/json"}
    data = {"username": username, "password": password}
    
    try:
        resp = requests.post(url, json=data, headers=headers, timeout=10)
        resp.raise_for_status()
        result = resp.json()
        token = result.get("access_token")
        print(f"✅ 登录成功，token: {token[:20]}...")
        return token
    except Exception as e:
        print(f"❌ 登录失败: {e}")
        if hasattr(e, 'response') and e.response:
            print(f"   响应: {e.response.text}")
        sys.exit(1)

def list_memos(token: str, page: int = 1, size: int = 100):
    """获取备忘录列表"""
    url = f"{BASE_URL}/api/v1/memos/"
    headers = {"Authorization": f"Bearer {token}"}
    params = {"page": page, "size": size}
    
    try:
        resp = requests.get(url, headers=headers, params=params, timeout=10)
        resp.raise_for_status()
        result = resp.json()
        return result
    except Exception as e:
        print(f"❌ 获取备忘录失败: {e}")
        if hasattr(e, 'response') and e.response:
            print(f"   响应: {e.response.text}")
        return None

def get_memo_detail(token: str, memo_id: str):
    """获取单个备忘录详情"""
    url = f"{BASE_URL}/api/v1/memos/{memo_id}"
    headers = {"Authorization": f"Bearer {token}"}
    
    try:
        resp = requests.get(url, headers=headers, timeout=10)
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        print(f"❌ 获取备忘录详情失败: {e}")
        return None

def main():
    # 从命令行参数或环境变量获取用户名密码
    if len(sys.argv) >= 3:
        username = sys.argv[1]
        password = sys.argv[2]
    else:
        # 尝试从环境变量获取
        username = input("请输入用户名: ").strip()
        password = input("请输入密码: ").strip()
    
    print(f"\n🔐 正在登录 {BASE_URL}...")
    print(f"   用户名: {username}")
    token = login(username, password)
    
    print(f"\n📋 正在获取备忘录列表...")
    result = list_memos(token)
    
    if result is None:
        print("❌ 无法获取备忘录列表")
        return
    
    # 打印完整响应
    print(f"\n📄 完整 API 响应:")
    print(json.dumps(result, indent=2, ensure_ascii=False))
    
    # 解析备忘录
    items = result.get("items", [])
    total = result.get("total", 0)
    page = result.get("page", 0)
    size = result.get("size", 0)
    
    print(f"\n📊 统计信息:")
    print(f"   总数: {total}")
    print(f"   当前页: {page}")
    print(f"   每页大小: {size}")
    print(f"   本页项目数: {len(items)}")
    
    print(f"\n📝 备忘录列表:")
    for i, item in enumerate(items, 1):
        memo_id = item.get("id", "N/A")
        content = item.get("content", "")
        tags = item.get("tags", [])
        created_at = item.get("created_at", "N/A")
        updated_at = item.get("updated_at", "N/A")
        
        print(f"\n   [{i}] ID: {memo_id}")
        print(f"       原始内容: {content[:100]}{'...' if len(content) > 100 else ''}")
        print(f"       标签: {tags}")
        print(f"       创建时间: {created_at}")
        print(f"       更新时间: {updated_at}")
        
        # 尝试解析 content 中的 JSON
        try:
            content_obj = json.loads(content)
            title = content_obj.get("title", "")
            memo_content = content_obj.get("content", "")
            print(f"       📌 解析后的标题: '{title}'")
            print(f"       📌 解析后的内容: '{memo_content[:50]}{'...' if len(memo_content) > 50 else ''}'")
        except Exception as e:
            print(f"       ⚠️ 内容不是 JSON 格式: {e}")
    
    # 获取第一个备忘录的详情（如果有）
    if items:
        first_id = items[0].get("id")
        print(f"\n🔍 获取第一个备忘录详情 (ID: {first_id}):")
        detail = get_memo_detail(token, first_id)
        if detail:
            print(json.dumps(detail, indent=2, ensure_ascii=False))

if __name__ == "__main__":
    main()
