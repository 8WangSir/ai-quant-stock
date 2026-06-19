import sys
import json
import baostock as bs
import time
import random

import os
os.environ['PYTHONIOENCODING'] = 'utf-8'

def ensure_login():
    """确保登录成功"""
    lg = bs.login()
    if lg.error_code != '0':
        print(f'登录失败: {lg.error_msg}', file=sys.stderr)
        sys.exit(1)

def to_baostock_code(code):
    """转换为 Baostock 格式"""
    if len(code) == 6:
        if code.startswith('6'):
            return f'sh.{code}'
        else:
            return f'sz.{code}'
    return code

def load_codes_json(codes_json_or_file):
    """解析股票代码 JSON（支持从文件读取）"""
    if codes_json_or_file.startswith('@'):
        with open(codes_json_or_file[1:], 'r', encoding='utf-8-sig') as f:
            return json.load(f)
    else:
        return json.loads(codes_json_or_file)

def fetch_stock_list():
    """获取股票列表"""
    ensure_login()
    print("正在获取股票列表...", file=sys.stderr)
    rs = bs.query_stock_basic()
    stocks = []
    
    while (rs.error_code == '0') & rs.next():
        row = rs.get_row_data()
        if len(row) < 6:
            continue
        code = row[0]
        name = row[1]
        ipo_date = row[2] if len(row) > 2 else None
        stock_type = row[4]
        status = row[5]
        
        if stock_type != '1' or status != '1':
            continue
            
        if code.startswith('sh.'):
            pure = code[3:]
            exchange = 'SH'
        elif code.startswith('sz.'):
            pure = code[3:]
            exchange = 'SZ'
        elif code.startswith('bj.'):
            pure = code[3:]
            exchange = 'BJ'
        else:
            continue
        
        stocks.append({
            'code': pure,
            'name': name,
            'exchange': exchange,
            'list_date': ipo_date if ipo_date else None,
            'industry': None
        })
    
    print(f"获取到 {len(stocks)} 只股票", file=sys.stderr)
    return stocks


def fetch_industry_data():
    """获取行业分类数据"""
    ensure_login()
    print("正在获取行业分类数据...", file=sys.stderr)
    
    rs = bs.query_stock_industry()
    industries = []
    
    while (rs.error_code == '0') & rs.next():
        row = rs.get_row_data()
        # row[0]=date, row[1]=code(sh.xxxxxx), row[2]=name, row[3]=industry
        code = row[1]
        if code.startswith('sh.'):
            pure = code[3:]
        elif code.startswith('sz.'):
            pure = code[3:]
        else:
            pure = code
        
        industry = row[3] if row[3] else None
        try:
            industry = industry.encode('latin1').decode('gbk')
        except:
            pass
        
        industries.append({
            'code': pure,
            'industry': industry
        })
    
    print(f"获取到 {len(industries)} 只股票的行业数据", file=sys.stderr)
    return industries


def fetch_industry_daily(start_date, end_date):
    """
    获取行业每日行情数据
    通过获取成分股的日K数据，按行业分组计算平均涨跌幅
    start_date: 起始日期，如 '2025-06-01'
    end_date: 结束日期，如 '2025-06-19'
    """
    ensure_login()
    print(f"正在获取行业每日行情数据 ({start_date} ~ {end_date})...", file=sys.stderr)
    
    # 第一步：获取所有股票的行业分类
    rs = bs.query_stock_industry()
    stock_industry_map = {}  # {code: industry}
    
    while (rs.error_code == '0') & rs.next():
        row = rs.get_row_data()
        code = row[1]
        industry = row[3]
        if code.startswith('sh.'):
            pure = code[3:]
        elif code.startswith('sz.'):
            pure = code[3:]
        else:
            pure = code
        stock_industry_map[pure] = industry
    
    print(f"获取到 {len(stock_industry_map)} 只股票的行业分类", file=sys.stderr)
    
    # 按行业分组股票代码
    industry_stocks = {}  # {industry: [codes]}
    for code, industry in stock_industry_map.items():
        if industry:
            if industry not in industry_stocks:
                industry_stocks[industry] = []
            industry_stocks[industry].append(code)
    
    # 第二步：获取各行业的代表性股票日K数据
    # 选取每个行业前5只股票作为代表以加快速度
    industry_data = {}  # {industry: {date: {'closes': [], 'pct_chgs': []}}}
    
    for industry, codes in industry_stocks.items():
        sample_codes = codes[:5]  # 只取5只代表股票
        industry_data[industry] = {}
        
        for code in sample_codes:
            bs_code = to_baostock_code(code)
            if not bs_code:
                continue
                
            rs = bs.query_history_k_data_plus(
                bs_code,
                "date,close,pctChg",
                start_date=start_date,
                end_date=end_date,
                frequency="d",
                adjustflag="3"
            )
            
            while (rs.error_code == '0') & rs.next():
                row = rs.get_row_data()
                trade_date = row[0]
                close = row[1]
                pct_chg = row[2]
                
                if trade_date not in industry_data[industry]:
                    industry_data[industry][trade_date] = {'closes': [], 'pct_chgs': []}
                
                if close:
                    industry_data[industry][trade_date]['closes'].append(float(close))
                if pct_chg:
                    industry_data[industry][trade_date]['pct_chgs'].append(float(pct_chg))
            
            time.sleep(0.03)
        
        if (len(industry_stocks)) % 100 == 0:
            print(f"已处理 {len(industry_stocks)} 个行业...", file=sys.stderr)
    
    # 第三步：计算行业平均数据
    result = []
    for industry, dates_data in industry_data.items():
        for trade_date, values in dates_data.items():
            closes = values['closes']
            pct_chgs = values['pct_chgs']
            
            if closes:
                avg_close = sum(closes) / len(closes)
                avg_pct_chg = sum(pct_chgs) / len(pct_chgs) if pct_chgs else 0
                
                result.append({
                    'industry_name': industry,
                    'trade_date': trade_date,
                    'close': round(avg_close, 4),
                    'change_percent': round(avg_pct_chg, 4),
                    'amount': 0,
                    'capital_flow': None
                })
    
    # 按日期和行业排序
    result.sort(key=lambda x: (x['trade_date'], x['industry_name']))
    
    print(f"获取到 {len(result)} 条行业行情数据", file=sys.stderr)
    return result

def fetch_daily_bars(codes, start_date, end_date):
    """获取日线数据"""
    ensure_login()
    bars = []
    total = len(codes)
    
    print(f"正在获取 {total} 只股票的日线数据 ({start_date} ~ {end_date})...", file=sys.stderr)
    
    for idx, code in enumerate(codes):
        bs_code = to_baostock_code(code)
        print(f"[{idx+1}/{total}] {bs_code}", file=sys.stderr)
        
        rs = bs.query_history_k_data_plus(
            bs_code,
            "date,code,open,high,low,close,preclose,volume,amount,adjustflag,turn,tradestatus,pctChg,isST",
            start_date=start_date,
            end_date=end_date,
            frequency="d",
            adjustflag="3"
        )
        
        while (rs.error_code == '0') & rs.next():
            row = rs.get_row_data()
            bars.append({
                'date': row[0],
                'code': code,
                'open': row[2],
                'high': row[3],
                'low': row[4],
                'close': row[5],
                'pre_close': row[6],
                'volume': row[7],
                'amount': row[8],
                'adjust_flag': row[9],
                'turnover_rate': row[10],
                'trade_status': row[11],
                'pct_chg': row[12],
                'is_st': row[13]
            })
        
        time.sleep(0.01)
    
    print(f"获取到 {len(bars)} 条K线数据", file=sys.stderr)
    return bars


def fetch_finance_data(codes, start_year, end_year):
    """
    获取财务数据（整合盈利能力、成长能力、偿债能力、现金流量）
    start_year: 起始年份，如 2024
    end_year: 结束年份，如 2025
    """
    ensure_login()
    finance_data = []
    total = len(codes)
    
    print(f"正在获取 {total} 只股票的财务数据 ({start_year}Q1 ~ {end_year}Q4)...", file=sys.stderr)
    
    for idx, code in enumerate(codes):
        bs_code = to_baostock_code(code)
        if (idx + 1) % 100 == 0:
            print(f"[{idx+1}/{total}] {bs_code}", file=sys.stderr)
        
        # 存储每只股票各季度的财务数据
        stock_finances = {}
        
        # 遍历每个季度
        for year in range(start_year, end_year + 1):
            for quarter in range(1, 5):
                try:
                    # 1. 盈利能力
                    rs_profit = bs.query_profit_data(code=bs_code, year=year, quarter=quarter)
                    while (rs_profit.error_code == '0') & rs_profit.next():
                        row = rs_profit.get_row_data()
                        stat_date = row[2]  # statDate
                        if stat_date and stat_date not in stock_finances:
                            stock_finances[stat_date] = {
                                'code': code,
                                'report_date': stat_date,
                                'roe': row[3],  # roeAvg
                                'revenue_growth': None,
                                'profit_growth': None,
                                'debt_ratio': None,
                                'cash_flow': None
                            }
                    time.sleep(0.05)
                    
                    # 2. 成长能力
                    rs_growth = bs.query_growth_data(code=bs_code, year=year, quarter=quarter)
                    while (rs_growth.error_code == '0') & rs_growth.next():
                        row = rs_growth.get_row_data()
                        stat_date = row[2]
                        if stat_date in stock_finances:
                            stock_finances[stat_date]['revenue_growth'] = row[3]  # YOYNI
                            stock_finances[stat_date]['profit_growth'] = row[5]   # YOYPNI
                    time.sleep(0.05)
                    
                    # 3. 偿债能力 - 使用资产负债表数据
                    rs_balance = bs.query_balance_data(code=bs_code, year=year, quarter=quarter)
                    while (rs_balance.error_code == '0') & rs_balance.next():
                        row = rs_balance.get_row_data()
                        stat_date = row[2]
                        if stat_date in stock_finances:
                            debt_ratio_val = row[7]  # liabilityToAsset
                            # 验证数据合理性：资产负债率应该在 0-1 之间
                            if debt_ratio_val:
                                try:
                                    val = float(debt_ratio_val)
                                    if 0 <= val <= 1:
                                        stock_finances[stat_date]['debt_ratio'] = debt_ratio_val
                                except ValueError:
                                    pass
                    time.sleep(0.05)
                    
                    # 4. 现金流量
                    rs_cash = bs.query_cash_flow_data(code=bs_code, year=year, quarter=quarter)
                    while (rs_cash.error_code == '0') & rs_cash.next():
                        row = rs_cash.get_row_data()
                        stat_date = row[2]
                        if stat_date in stock_finances:
                            stock_finances[stat_date]['cash_flow'] = row[8]  # CFOToNP
                    time.sleep(0.05)
                    
                except AttributeError as e:
                    print(f"获取 {bs_code} {year}Q{quarter} 财务数据失败(API不存在): {e}", file=sys.stderr)
                except Exception as e:
                    print(f"获取 {bs_code} {year}Q{quarter} 财务数据失败: {e}", file=sys.stderr)
        
        # 合并到结果列表
        for stat_date, finance in stock_finances.items():
            finance_data.append(finance)
        
        if (idx + 1) % 100 == 0:
            print(f"已处理 {idx+1}/{total} 只股票，获取 {len(finance_data)} 条财务数据", file=sys.stderr)
    
    print(f"财务数据获取完成: {len(finance_data)} 条记录", file=sys.stderr)
    return finance_data

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"success": False, "error": "no command"}))
        sys.exit(1)
    
    cmd = sys.argv[1]
    
    try:
        if cmd == "stock_list":
            stocks = fetch_stock_list()
            print(json.dumps({"success": True, "data": stocks}, ensure_ascii=False))
        elif cmd == "daily_full":
            if len(sys.argv) < 4:
                print(json.dumps({"success": False, "error": "need start_date, end_date, codes"}))
                sys.exit(1)
            codes = load_codes_json(sys.argv[4])
            bars = fetch_daily_bars(codes, sys.argv[2], sys.argv[3])
            print(json.dumps({"success": True, "data": bars}, ensure_ascii=False))
        elif cmd == "daily_incr":
            if len(sys.argv) < 3:
                print(json.dumps({"success": False, "error": "need date, codes"}))
                sys.exit(1)
            codes = load_codes_json(sys.argv[3])
            bars = fetch_daily_bars(codes, sys.argv[2], sys.argv[2])
            print(json.dumps({"success": True, "data": bars}, ensure_ascii=False))
        elif cmd == "finance_full":
            # 格式: python baostock_client.py finance_full start_year end_year @codes.json
            if len(sys.argv) < 5:
                print(json.dumps({"success": False, "error": "need start_year, end_year, codes"}))
                sys.exit(1)
            codes = load_codes_json(sys.argv[4])
            start_year = int(sys.argv[2])
            end_year = int(sys.argv[3])
            data = fetch_finance_data(codes, start_year, end_year)
            print(json.dumps({"success": True, "data": data}, ensure_ascii=False))
        elif cmd == "industry":
            # 获取行业分类数据
            data = fetch_industry_data()
            print(json.dumps({"success": True, "data": data}, ensure_ascii=False))
        elif cmd == "industry_daily":
            # 获取行业指数K线数据
            if len(sys.argv) < 4:
                print(json.dumps({"success": False, "error": "need start_date, end_date"}))
                sys.exit(1)
            start_date = sys.argv[2]
            end_date = sys.argv[3]
            data = fetch_industry_daily(start_date, end_date)
            print(json.dumps({"success": True, "data": data}, ensure_ascii=False))
        else:
            print(json.dumps({"success": False, "error": f"unknown command {cmd}"}))
    except Exception as e:
        print(json.dumps({"success": False, "error": str(e)}), file=sys.stderr)
        sys.exit(1)
