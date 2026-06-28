#!/usr/bin/env python3
"""
时间解析调试脚本
专门调查"下午3点"变"下午4点"的原因
"""

import sys
sys.path.insert(0, 'D:/Gitproject/ChronoSync')

from datetime import datetime, timedelta
from pytz import timezone
import re
from dateutil import parser

# 北京时区
BEIJING_TZ = timezone('Asia/Shanghai')


def get_beijing_time():
    """获取当前北京时间"""
    return datetime.now(BEIJING_TZ)


def parse_time_debug(time_str: str):
    """调试版时间解析，打印每一步"""
    print(f"\n{'='*60}")
    print(f"原始输入: '{time_str}'")
    print(f"{'='*60}")
    
    if isinstance(time_str, datetime):
        return time_str
    if not time_str:
        return get_beijing_time()
    
    now = get_beijing_time()
    print(f"当前北京时间: {now}")
    
    time_str = str(time_str).strip()
    print(f"1. 去除空格后: '{time_str}'")
    
    # 处理相对日期词
    ref_date = get_beijing_time()  # 简化版
    
    # 处理时间格式 - **这里可能有问题**
    print(f"2. 替换'点'为':'前: '{time_str}'")
    time_str_modified = time_str.replace('点', ':').replace('：', ':')
    print(f"3. 替换'点'为':'后: '{time_str_modified}'")
    
    # 处理上午/下午/晚上
    is_pm = False
    if '下午' in time_str_modified or '晚上' in time_str_modified or '晚' in time_str_modified:
        is_pm = True
        print(f"4. 检测到下午/晚上标记, is_pm = True")
        time_str_modified = re.sub(r'下午|晚上|晚', '', time_str_modified).strip()
        print(f"5. 移除'下午/晚上'后: '{time_str_modified}'")
    
    if '上午' in time_str_modified or '早上' in time_str_modified or '早' in time_str_modified:
        is_pm = False
        print(f"4. 检测到上午/早上标记, is_pm = False")
        time_str_modified = re.sub(r'上午|早上|早', '', time_str_modified).strip()
        print(f"5. 移除'上午/早上'后: '{time_str_modified}'")
    
    # 替换相对日期
    replacements = {
        r'今天': now.strftime('%Y-%m-%d'),
        r'明天': (now + timedelta(days=1)).strftime('%Y-%m-%d'),
        r'后天': (now + timedelta(days=2)).strftime('%Y-%m-%d'),
    }
    
    for pattern, replacement in replacements.items():
        if re.search(pattern, time_str_modified):
            print(f"6. 替换日期 '{pattern}' -> '{replacement}'")
            time_str_modified = re.sub(pattern, replacement, time_str_modified)
            print(f"   结果: '{time_str_modified}'")
    
    # 解析时间
    print(f"\n7. 最终待解析字符串: '{time_str_modified}'")
    
    try:
        if re.search(r'\d{4}[-年]', time_str_modified) or re.search(r'\d{1,2}月', time_str_modified):
            print("   使用完整日期解析模式")
            dt = parser.parse(time_str_modified)
        else:
            print("   使用默认日期解析模式")
            default_dt = ref_date.replace(hour=0, minute=0, second=0)
            print(f"   默认日期: {default_dt}")
            dt = parser.parse(time_str_modified, default=default_dt)
        
        print(f"   解析结果(无时区调整): {dt}")
        
        if is_pm and dt.hour < 12:
            print(f"   检测到下午标记，小时{dt.hour} < 12，加12小时")
            dt = dt + timedelta(hours=12)
            print(f"   调整后: {dt}")
        
        print(f"\n✅ 最终解析结果: {dt}")
        return dt
        
    except Exception as e:
        print(f"   解析出错: {e}")
        # 备用解析
        combined = f"{ref_date.date().isoformat()} {time_str_modified}"
        print(f"   尝试备用解析: '{combined}'")
        dt = parser.parse(combined, fuzzy=True)
        print(f"\n✅ 备用解析结果: {dt}")
        return dt


def test_cases():
    """测试各种时间输入"""
    test_inputs = [
        "明天下午3点",
        "明天下午3:00",
        "明天15:00",
        "明天3点",
        "明天下午三点",
        "今天下午3点",
        "明天晚上8点",
        "明天上午9点",
    ]
    
    print("\n" + "="*60)
    print("时间解析调试测试")
    print("="*60)
    
    for input_str in test_inputs:
        result = parse_time_debug(input_str)
        print(f"\n{'='*60}")
        print(f"输入: '{input_str}'")
        print(f"输出: {result.strftime('%Y-%m-%d %H:%M')}")
        print(f"{'='*60}")


def test_actual_agent_parsing():
    """测试实际 Agent 使用的解析逻辑"""
    print("\n\n" + "="*60)
    print("测试实际 Agent 解析逻辑")
    print("="*60)
    
    # 模拟 Agent 中的 _parse_time 方法
    from app.agents.wanji_agent import WanjiAgent
    from unittest.mock import MagicMock
    
    # 创建模拟对象
    mock_db = MagicMock()
    mock_user_id = "test-user-id"
    
    agent = WanjiAgent(mock_db, mock_user_id)
    
    test_cases = [
        "明天下午3点",
        "明天下午3:00", 
        "明天15:00",
    ]
    
    for case in test_cases:
        result = agent._parse_time(case)
        print(f"\n输入: '{case}'")
        print(f"Agent 解析结果: {result}")
        print(f"格式化: {result.strftime('%Y-%m-%d %H:%M')}")


if __name__ == "__main__":
    print("选择测试模式:")
    print("1. 基础调试测试")
    print("2. 实际 Agent 解析测试")
    
    try:
        test_actual_agent_parsing()
    except Exception as e:
        print(f"Agent 测试失败: {e}")
        print("运行基础调试测试...")
        test_cases()
