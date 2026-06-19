import sys
sys.path.insert(0, '/app/python/data_sync')
from baostock_client import get_stock_list, ensure_login, logout

ensure_login()
stocks = get_stock_list()
print(f"-- Total stocks: {len(stocks)}")

print("INSERT INTO stock_info (code, name, market) VALUES ")
values = []
for s in stocks:
    code = s['code']
    name = s['name'].replace("'", "''")
    market = s['exchange']
    values.append(f"('{code}', '{name}', '{market}')")

# 每1000条分一批
batch_size = 1000
for i in range(0, len(values), batch_size):
    batch = values[i:i+batch_size]
    print(",".join(batch) + ";")
    if i + batch_size < len(values):
        print("INSERT INTO stock_info (code, name, market) VALUES ")

logout()
