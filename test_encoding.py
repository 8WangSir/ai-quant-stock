#!/usr/bin/env python3
"""测试股票名称编码"""
import sys
import json
sys.path.insert(0, 'c:/Users/wangw/ai-quant-stock/python/data_sync')
from baostock_client import get_stock_list

print("开始获取股票列表...")
stocks = get_stock_list()
print(f"共获取 {len(stocks)} 只股票")
print("\n前10只股票：")
for i, s in enumerate(stocks[:10]):
    print(f"{i+1}. {s['code']} - {s['name']}")

# 保存测试结果
with open('test_stocks.json', 'w', encoding='utf-8') as f:
    json.dump(stocks[:50], f, ensure_ascii=False, indent=2)
print("\n测试结果已保存到 test_stocks.json")
