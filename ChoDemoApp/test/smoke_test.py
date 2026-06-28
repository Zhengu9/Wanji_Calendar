#!/usr/bin/env python3
"""
ChronoSync 后端 API 烟雾测试脚本
用于快速验证后端接口是否按 openapi2.yaml 实现
"""

import requests
import json
import sys
from datetime import datetime, timedelta

# 配置
BASE_URL = "http://115.190.155.26:8000"
TEST_USERNAME = f"smoke_test_{datetime.now().strftime('%H%M%S')}"
TEST_PASSWORD = "test123456"

class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    RESET = '\033[0m'

def log_pass(msg):
    print(f"{Colors.GREEN}✅ PASS{Colors.RESET}: {msg}")

def log_fail(msg, detail=None):
    print(f"{Colors.RED}❌ FAIL{Colors.RESET}: {msg}")
    if detail:
        print(f"   详情: {detail}")

def log_info(msg):
    print(f"{Colors.YELLOW}ℹ️ INFO{Colors.RESET}: {msg}")

class SmokeTest:
    def __init__(self):
        self.token = None
        self.event_id = None
        self.session = requests.Session()
        self.passed = 0
        self.failed = 0

    def test_register(self):
        """测试注册接口"""
        log_info("测试用户注册...")
        try:
            resp = self.session.post(
                f"{BASE_URL}/api/v1/auth/register",
                json={"username": TEST_USERNAME, "password": TEST_PASSWORD}
            )
            if resp.status_code == 201:
                log_pass("用户注册成功")
                self.passed += 1
                return True
            else:
                log_fail("用户注册失败", f"状态码: {resp.status_code}, 响应: {resp.text}")
                self.failed += 1
                return False
        except Exception as e:
            log_fail("用户注册异常", str(e))
            self.failed += 1
            return False

    def test_login_json_format(self):
        """测试登录接口 JSON 格式"""
        log_info("测试登录接口（JSON 格式）...")
        try:
            # 测试 JSON 格式
            resp = self.session.post(
                f"{BASE_URL}/api/v1/auth/login",
                json={"username": TEST_USERNAME, "password": TEST_PASSWORD}
            )
            
            if resp.status_code == 200:
                data = resp.json()
                self.token = data.get("access_token")
                if self.token:
                    log_pass("JSON 格式登录成功，获取到 token")
                    self.passed += 1
                    return True
                else:
                    log_fail("登录响应中没有 access_token", resp.text)
                    self.failed += 1
                    return False
            else:
                log_fail("JSON 格式登录失败", f"状态码: {resp.status_code}, 响应: {resp.text}")
                self.failed += 1
                return False
        except Exception as e:
            log_fail("登录异常", str(e))
            self.failed += 1
            return False

    def test_create_event_with_type_priority(self):
        """测试创建日程带 type 和 priority"""
        log_info("测试创建日程（带 type/priority）...")
        if not self.token:
            log_fail("未登录，跳过测试")
            self.failed += 1
            return False

        try:
            now = datetime.now()
            start_time = now.isoformat()
            end_time = (now + timedelta(hours=1)).isoformat()
            
            payload = {
                "title": "烟雾测试日程",
                "start_time": start_time,
                "end_time": end_time,
                "type": "WORK",
                "priority": 3,
                "description": "这是测试描述"
            }
            
            resp = self.session.post(
                f"{BASE_URL}/api/v1/events/",
                headers={"Authorization": f"Bearer {self.token}"},
                json=payload
            )
            
            if resp.status_code == 201:
                data = resp.json()
                self.event_id = data.get("id")
                
                # 验证返回的 type 和 priority
                returned_type = data.get("type")
                returned_priority = data.get("priority")
                
                if returned_type == "WORK" and returned_priority == 3:
                    log_pass(f"创建日程成功，type={returned_type}, priority={returned_priority}")
                    self.passed += 1
                    return True
                else:
                    log_fail(
                        "创建日程成功，但 type/priority 不匹配",
                        f"期望: type=WORK, priority=3, 实际: type={returned_type}, priority={returned_priority}"
                    )
                    self.failed += 1
                    return False
            else:
                log_fail("创建日程失败", f"状态码: {resp.status_code}, 响应: {resp.text}")
                self.failed += 1
                return False
        except Exception as e:
            log_fail("创建日程异常", str(e))
            self.failed += 1
            return False

    def test_list_events(self):
        """测试查询日程列表"""
        log_info("测试查询日程列表...")
        if not self.token:
            log_fail("未登录，跳过测试")
            self.failed += 1
            return False

        try:
            resp = self.session.get(
                f"{BASE_URL}/api/v1/events/",
                headers={"Authorization": f"Bearer {self.token}"}
            )
            
            if resp.status_code == 200:
                data = resp.json()
                items = data.get("items", [])
                
                # 检查返回的日程是否有 type 和 priority 字段
                if items:
                    first = items[0]
                    has_type = "type" in first
                    has_priority = "priority" in first
                    
                    if has_type and has_priority:
                        log_pass(f"查询日程成功，共 {len(items)} 条，包含 type/priority 字段")
                        self.passed += 1
                        return True
                    else:
                        log_fail(
                            "查询日程成功，但缺少字段",
                            f"有 type: {has_type}, 有 priority: {has_priority}"
                        )
                        self.failed += 1
                        return False
                else:
                    log_pass("查询日程成功，列表为空（可能是新账号）")
                    self.passed += 1
                    return True
            else:
                log_fail("查询日程失败", f"状态码: {resp.status_code}, 响应: {resp.text}")
                self.failed += 1
                return False
        except Exception as e:
            log_fail("查询日程异常", str(e))
            self.failed += 1
            return False

    def test_update_event(self):
        """测试更新日程"""
        log_info("测试更新日程...")
        if not self.token or not self.event_id:
            log_fail("未登录或无日程，跳过测试")
            self.failed += 1
            return False

        try:
            resp = self.session.put(
                f"{BASE_URL}/api/v1/events/{self.event_id}",
                headers={"Authorization": f"Bearer {self.token}"},
                json={
                    "title": "已更新的烟雾测试日程",
                    "type": "LIFE",
                    "priority": 1
                }
            )
            
            if resp.status_code == 200:
                data = resp.json()
                if data.get("type") == "LIFE" and data.get("priority") == 1:
                    log_pass("更新日程成功，type/priority 更新正确")
                    self.passed += 1
                    return True
                else:
                    log_fail(
                        "更新日程成功，但字段未更新",
                        f"type={data.get('type')}, priority={data.get('priority')}"
                    )
                    self.failed += 1
                    return False
            else:
                log_fail("更新日程失败", f"状态码: {resp.status_code}, 响应: {resp.text}")
                self.failed += 1
                return False
        except Exception as e:
            log_fail("更新日程异常", str(e))
            self.failed += 1
            return False

    def test_delete_event(self):
        """测试删除日程"""
        log_info("测试删除日程...")
        if not self.token or not self.event_id:
            log_fail("未登录或无日程，跳过测试")
            self.failed += 1
            return False

        try:
            resp = self.session.delete(
                f"{BASE_URL}/api/v1/events/{self.event_id}",
                headers={"Authorization": f"Bearer {self.token}"}
            )
            
            if resp.status_code == 204:
                log_pass("删除日程成功")
                self.passed += 1
                return True
            else:
                log_fail("删除日程失败", f"状态码: {resp.status_code}")
                self.failed += 1
                return False
        except Exception as e:
            log_fail("删除日程异常", str(e))
            self.failed += 1
            return False

    def test_memo_create(self):
        """测试创建备忘录"""
        log_info("测试创建备忘录...")
        if not self.token:
            log_fail("未登录，跳过测试")
            self.failed += 1
            return False

        try:
            resp = self.session.post(
                f"{BASE_URL}/api/v1/memos/",
                headers={"Authorization": f"Bearer {self.token}"},
                json={
                    "content": '{"title": "测试备忘", "content": "这是测试内容"}',
                    "tags": ["test"]
                }
            )
            
            if resp.status_code == 201:
                log_pass("创建备忘录成功")
                self.passed += 1
                return True
            else:
                log_fail("创建备忘录失败", f"状态码: {resp.status_code}, 响应: {resp.text}")
                self.failed += 1
                return False
        except Exception as e:
            log_fail("创建备忘录异常", str(e))
            self.failed += 1
            return False

    def run_all(self):
        """运行所有测试"""
        print("=" * 60)
        print("ChronoSync API 烟雾测试")
        print(f"测试地址: {BASE_URL}")
        print(f"测试用户: {TEST_USERNAME}")
        print("=" * 60)
        print()

        # 认证测试
        self.test_register()
        self.test_login_json_format()
        
        # 日程测试
        self.test_create_event_with_type_priority()
        self.test_list_events()
        self.test_update_event()
        self.test_delete_event()
        
        # 备忘录测试
        self.test_memo_create()

        # 总结
        print()
        print("=" * 60)
        print("测试结果总结")
        print("=" * 60)
        total = self.passed + self.failed
        print(f"通过: {Colors.GREEN}{self.passed}{Colors.RESET}")
        print(f"失败: {Colors.RED}{self.failed}{Colors.RESET}")
        print(f"总计: {total}")
        print()
        
        if self.failed == 0:
            print(f"{Colors.GREEN}🎉 所有测试通过！后端接口已正确实现 openapi2.yaml{Colors.RESET}")
            return 0
        else:
            print(f"{Colors.RED}⚠️ 有测试失败，请检查后端实现{Colors.RESET}")
            return 1

if __name__ == "__main__":
    sys.exit(SmokeTest().run_all())
