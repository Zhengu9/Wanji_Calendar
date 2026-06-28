#!/usr/bin/env python3
"""
同步问题排查脚本
测试后端是否有 AI 创建的日程数据
"""

import requests
import json
import sys
from datetime import datetime, timedelta

BASE_URL = "http://115.190.155.26:8000"

def login(username, password):
    """登录获取 token"""
    try:
        resp = requests.post(
            f"{BASE_URL}/api/v1/auth/login",
            json={"username": username, "password": password},
            timeout=10
        )
        if resp.status_code == 200:
            data = resp.json()
            return data.get("access_token")
        else:
            print(f"❌ 登录失败: {resp.status_code}")
            print(f"   响应: {resp.text}")
            return None
    except Exception as e:
        print(f"❌ 登录异常: {e}")
        return None

def list_events(token):
    """获取云端日程列表"""
    try:
        resp = requests.get(
            f"{BASE_URL}/api/v1/events/",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10
        )
        if resp.status_code == 200:
            return resp.json()
        else:
            print(f"❌ 获取日程失败: {resp.status_code}")
            print(f"   响应: {resp.text}")
            return None
    except Exception as e:
        print(f"❌ 获取日程异常: {e}")
        return None

def check_sync_status(username, password):
    """检查同步状态"""
    print("=" * 60)
    print("同步问题排查")
    print("=" * 60)
    print()
    
    # 1. 登录
    print(f"1. 登录用户: {username}")
    token = login(username, password)
    if not token:
        return
    print("✅ 登录成功")
    print(f"   Token: {token[:20]}...")
    print()
    
    # 2. 获取云端日程
    print("2. 从云端获取日程列表...")
    events_data = list_events(token)
    if not events_data:
        return
    
    items = events_data.get("items", [])
    total = events_data.get("total", 0)
    
    print(f"✅ 云端共有 {total} 条日程")
    print()
    
    # 3. 显示最近创建的日程（按时间倒序）
    if items:
        # 按 created_at 排序，最新的在前面
        sorted_items = sorted(
            items, 
            key=lambda x: x.get("created_at", ""), 
            reverse=True
        )
        
        print("3. 最近创建的日程（最多显示5条）:")
        print("-" * 60)
        
        for i, event in enumerate(sorted_items[:5], 1):
            title = event.get("title", "无标题")
            start_time = event.get("start_time", "未知时间")
            created_at = event.get("created_at", "未知")
            event_id = event.get("id", "无ID")[:8]
            
            print(f"   {i}. {title}")
            print(f"      时间: {start_time}")
            print(f"      创建时间: {created_at}")
            print(f"      ID: {event_id}...")
            print()
        
        # 4. 检查是否有今天创建的日程
        today = datetime.now().date()
        today_str = today.isoformat()
        
        today_events = [
            e for e in items 
            if e.get("created_at", "").startswith(today_str)
        ]
        
        if today_events:
            print(f"✅ 今天创建的日程: {len(today_events)} 条")
        else:
            print(f"⚠️ 今天没有新创建的日程")
        
        print()
        
        # 5. 判断结论
        print("=" * 60)
        print("结论")
        print("=" * 60)
        
        if total == 0:
            print("❌ 后端云端没有任何日程数据")
            print("   → 可能是 AI 创建日程失败（后端问题）")
            print("   → 或者从未创建过日程")
        elif today_events:
            print("✅ 后端有今天创建的日程数据")
            print("   → 如果前端没有显示，是前端同步问题")
        else:
            print("⚠️ 后端有日程数据，但没有今天创建的")
            print("   → AI 可能没创建成功，或者创建到了其他日期")
            print(f"   → 建议检查 AI 创建的日程日期是否在 {today_str} 附近")
    else:
        print("❌ 后端云端日程列表为空")
        print("   → 可能是 AI 创建日程失败（后端问题）")
    
    print()
    print("=" * 60)

def test_agent_create(username, password, text):
    """测试 AI 创建日程"""
    print("=" * 60)
    print(f"测试 AI 创建日程: {text}")
    print("=" * 60)
    print()
    
    # 登录
    token = login(username, password)
    if not token:
        return
    
    # 记录创建前的日程数
    before = list_events(token)
    before_count = before.get("total", 0) if before else 0
    print(f"创建前云端日程数: {before_count}")
    print()
    
    # 调用 AI
    print(f"发送请求: {text}")
    try:
        resp = requests.post(
            f"{BASE_URL}/api/v1/agent/process",
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json"
            },
            json={"text": text},
            timeout=30
        )
        
        if resp.status_code == 200:
            data = resp.json()
            print(f"✅ AI 响应: {data.get('reply', '无回复')}")
            print(f"   action: {data.get('action')}")
            print(f"   entity: {data.get('entity')}")
        else:
            print(f"❌ AI 请求失败: {resp.status_code}")
            print(f"   响应: {resp.text}")
            return
    except Exception as e:
        print(f"❌ AI 请求异常: {e}")
        return
    
    print()
    
    # 再次获取日程数
    after = list_events(token)
    after_count = after.get("total", 0) if after else 0
    print(f"创建后云端日程数: {after_count}")
    
    if after_count > before_count:
        print("✅ 日程创建成功！")
    else:
        print("❌ 日程没有增加，创建可能失败")
    
    print()

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("用法:")
        print(f"  python {sys.argv[0]} <用户名> <密码>")
        print()
        print("示例:")
        print(f"  python {sys.argv[0]} testuser test123")
        print()
        print("可选：添加测试创建参数，自动测试 AI 创建日程")
        print(f"  python {sys.argv[0]} testuser test123 --create")
        sys.exit(1)
    
    username = sys.argv[1]
    password = sys.argv[2]
    
    # 检查同步状态
    check_sync_status(username, password)
    
    # 如果带 --create 参数，测试创建日程
    if len(sys.argv) > 3 and sys.argv[3] == "--create":
        test_text = "帮我安排明天下午3点的测试会议"
        test_agent_create(username, password, test_text)
        
        # 再次检查
        print("\n再次检查同步状态...\n")
        check_sync_status(username, password)
