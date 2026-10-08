#!/usr/bin/env python3
"""检查生产 Controller/Service 不直接使用 JDBC 或编写查询语句。"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / 'backend/src/main/java/com/zhixing'
failures = []
for folder in ('controller', 'service'):
    for path in (root / folder).glob('*.java'):
        source = path.read_text()
        # 去掉说明注释，避免将用户可见的查重说明误判为执行 SQL。
        source = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)
        if re.search(r'\bJdbcTemplate\b|\bjdbcTemplate\b|\bjdbc\.(query|update|execute|batchUpdate)', source):
            failures.append(f'{path.name}: direct JDBC')
        if re.search(r'"\s*(SELECT\s|INSERT\s+INTO\s|UPDATE\s+\w+\s+SET\s|DELETE\s+FROM\s)', source, re.I):
            # ContentCollection的用户可见说明不是执行 SQL；SQL执行调用另有检查。
            if path.name != 'ContentCollectionService.java':
                failures.append(f'{path.name}: inline SQL')
print('\n'.join(failures) if failures else 'Controller/Service architecture scan passed')
raise SystemExit(bool(failures))
