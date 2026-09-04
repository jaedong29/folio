#!/usr/bin/env python3
"""KIND 상장법인목록에서 KOSPI/KOSDAQ 검색용 정적 카탈로그를 만든다."""

from __future__ import annotations

import json
import re
import tempfile
import urllib.request
from datetime import datetime, timedelta, timezone
from html.parser import HTMLParser
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
OUTPUT = PROJECT_ROOT / "src/main/resources/static/data/krx-listed-securities.json"
KST = timezone(timedelta(hours=9))
SOURCES = {
    "KOSPI": "https://kind.krx.co.kr/corpgeneral/corpList.do?method=download&marketType=stockMkt",
    "KOSDAQ": "https://kind.krx.co.kr/corpgeneral/corpList.do?method=download&marketType=kosdaqMkt",
}


class TableParser(HTMLParser):
    """KIND가 Excel로 내려주는 HTML table을 표의 행과 셀로 변환한다."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.rows: list[list[str]] = []
        self._row: list[str] | None = None
        self._cell: list[str] | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "tr":
            self._row = []
        elif tag in {"td", "th"} and self._row is not None:
            self._cell = []

    def handle_data(self, data: str) -> None:
        if self._cell is not None:
            self._cell.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag in {"td", "th"} and self._row is not None and self._cell is not None:
            value = re.sub(r"\s+", " ", "".join(self._cell)).strip()
            self._row.append(value)
            self._cell = None
        elif tag == "tr" and self._row is not None:
            if self._row:
                self.rows.append(self._row)
            self._row = None


def download(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return response.read().decode("euc-kr", errors="replace")


def parse_market(expected_market: str, html: str) -> list[dict[str, str]]:
    parser = TableParser()
    parser.feed(html)
    if not parser.rows:
        raise RuntimeError(f"{expected_market} 목록이 비어 있습니다.")

    header = parser.rows[0]
    try:
        name_index = header.index("회사명")
        market_index = header.index("시장구분")
        code_index = header.index("종목코드")
    except ValueError as error:
        raise RuntimeError(f"KIND 응답 형식이 변경되었습니다: {header}") from error

    market_names = {"유가": "KOSPI", "코스닥": "KOSDAQ"}
    securities: list[dict[str, str]] = []
    for row in parser.rows[1:]:
        if len(row) <= max(name_index, market_index, code_index):
            continue
        name = row[name_index].strip()
        code = row[code_index].strip().zfill(6)
        market = market_names.get(row[market_index].strip())
        if market != expected_market or not name or not re.fullmatch(r"\d{6}", code):
            continue
        securities.append({"name": name, "code": code, "market": market})
    return securities


def main() -> None:
    merged: dict[tuple[str, str], dict[str, str]] = {}
    for market, url in SOURCES.items():
        for security in parse_market(market, download(url)):
            merged[(security["market"], security["code"])] = security

    securities = sorted(
        merged.values(), key=lambda item: (item["name"].casefold(), item["market"], item["code"])
    )
    if len(securities) < 2_000:
        raise RuntimeError(f"상장종목 수가 예상보다 적습니다: {len(securities)}")

    payload = {
        "source": "KRX KIND 상장법인목록",
        "sourceUrl": "https://kind.krx.co.kr/corpgeneral/corpList.do?method=loadInitPage",
        "generatedAt": datetime.now(KST).isoformat(timespec="seconds"),
        "markets": ["KOSPI", "KOSDAQ"],
        "count": len(securities),
        "securities": securities,
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", dir=OUTPUT.parent, delete=False, suffix=".tmp"
    ) as temporary:
        json.dump(payload, temporary, ensure_ascii=False, separators=(",", ":"))
        temporary.write("\n")
        temporary_path = Path(temporary.name)
    temporary_path.replace(OUTPUT)
    print(f"Created {OUTPUT} ({len(securities)} securities)")


if __name__ == "__main__":
    main()
