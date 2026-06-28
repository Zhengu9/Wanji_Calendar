#!/usr/bin/env python3
"""测试后端日期格式要求"""

import requests
import sys

BASE_URL = "http://115.190.155.26:8000"

def login(username, password):
    resp = requests.post(
        f"{BASE_URL}/api/v1/auth/login",
        json={"username": username, "password": password}
    )
    return resp.json().get("access_token")

def test_list_events(token, start_date, end_date):
    """测试不同的日期格式"""
    params = {"page": "1", "size": "10"}
    if start_date:
        params["start_date"] = start_date
    if end_date:
        params["end_date"] = end_date
    
    print(f"\n测试: start_date={start_date}, end_date={end_date}")
    resp = requests.get(
        f"{BASE_URL}/api/v1/events/",
        headers={"Authorization": f"Bearer {token}"},
        params=params
    )
    print(f"状态码: {resp.status_code}")
    if resp.status_code != 200:
        print(f"响应: {resp.text[:200]}")
    else:
        data = resp.json()
        print(f"成功: 共 {data.get('total', 0)} 条日程")
    return resp.status_code == 200

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(f"用法: python {sys.argv[0]} <用户名> <密码>")
        sys.exit(1)
    
    username = sys.argv[1]
    password = sys.argv[2]
    
    token = login(username, password)
    if not token:
        print("登录失败")
        sys.exit(1)
    
    print("测试不同的日期格式:")
    
    # 测试 1: 标准 ISO 日期 (yyyy-MM-dd)
    test_list_events(token, "2025-09-16", "2027-03-15")
    
    # 测试 2: 不带日期参数
    test_list_events(token, None, None)
    
    # 测试 3: ISO DateTime 格式
    test_list_events(token, "2025-09-16T00:00:00", "2027-03-15T23:59:59")
    
    # 测试 4: 带时区的 ISO 格式
    test_list_events(token, "2025-09-16T00:00:00+08:00", "2027-03-15T23:59:59+08:00")
