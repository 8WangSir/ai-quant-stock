import sys
import json
import baostock as bs
import time
import random

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
        with open(codes_json_or_file[1:], 'r', encoding='utf-8') as f:
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
        else:
            print(json.dumps({"success": False, "error": f"unknown command {cmd}"}))
    except Exception as e:
        print(json.dumps({"success": False, "error": str(e)}), file=sys.stderr)
        sys.exit(1)
