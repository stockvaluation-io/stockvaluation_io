"""
Thesis generator for notebook sessions.
Local-first implementation that uses configured LLM provider when available,
with deterministic fallback output when LLM is unavailable.
"""

from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass
from typing import Any, Dict, Generator, List, Optional

from services.llm_service import get_llm_service

logger = logging.getLogger(__name__)


@dataclass
class ThesisPreview:
    title: str
    summary: str
    conviction: int
    key_assumptions: List[str]
    risks: List[str]
    fair_value: float
    current_price: float
    upside_pct: float
    target_timeframe: str

    def to_dict(self) -> Dict[str, Any]:
        return {
            "title": self.title,
            "summary": self.summary,
            "conviction": self.conviction,
            "key_assumptions": self.key_assumptions,
            "risks": self.risks,
            "fair_value": self.fair_value,
            "current_price": self.current_price,
            "upside_pct": self.upside_pct,
            "target_timeframe": self.target_timeframe,
        }


class ThesisGenerator:
    def generate_thesis_preview(
        self,
        cells: List[Dict[str, Any]],
        dcf_data: Dict[str, Any],
        ticker: str,
        company_name: str,
    ) -> ThesisPreview:
        fair_value, current_price, upside_pct = self._extract_dcf_metrics(dcf_data)

        prompt = self._build_prompt(
            ticker=ticker,
            company_name=company_name,
            fair_value=fair_value,
            current_price=current_price,
            upside_pct=upside_pct,
            cells=cells,
        )

        llm_service = get_llm_service()
        try:
            if llm_service.is_available():
                response = llm_service._invoke_text_completion(  # pylint: disable=protected-access
                    prompt=prompt,
                    provider=None,
                    max_tokens=1600,
                    temperature=0.2,
                )
                return self._parse_response(response, fair_value, current_price, upside_pct, ticker)
        except Exception as exc:
            logger.warning("Thesis generation via LLM failed; using fallback. error=%s", exc)

        return self._fallback_preview(ticker, company_name, fair_value, current_price, upside_pct)

    def generate_thesis_preview_stream(
        self,
        cells: List[Dict[str, Any]],
        dcf_data: Dict[str, Any],
        ticker: str,
        company_name: str,
    ) -> Generator[str, None, None]:
        fair_value, current_price, upside_pct = self._extract_dcf_metrics(dcf_data)
        prompt = self._build_prompt(
            ticker=ticker,
            company_name=company_name,
            fair_value=fair_value,
            current_price=current_price,
            upside_pct=upside_pct,
            cells=cells,
        )

        llm_service = get_llm_service()
        try:
            if llm_service.is_available():
                for chunk in llm_service.stream_chat(
                    messages=[{"role": "user", "content": prompt}],
                    system_prompt="Return only JSON.",
                    temperature=0.2,
                    max_tokens=1600,
                ):
                    yield chunk
                return
        except Exception as exc:
            logger.warning("Thesis stream via LLM failed; using fallback. error=%s", exc)

        yield json.dumps(
            self._fallback_preview(ticker, company_name, fair_value, current_price, upside_pct).to_dict()
        )

    def _extract_dcf_metrics(self, dcf_data: Dict[str, Any]) -> tuple[float, float, float]:
        company_dto = dcf_data.get("companyDTO", {}) if isinstance(dcf_data, dict) else {}

        fair_value = self._to_float(
            company_dto.get("estimatedValuePerShare")
            or dcf_data.get("intrinsicValue")
            or dcf_data.get("intrinsic_value")
            or dcf_data.get("fair_value")
        )
        current_price = self._to_float(
            company_dto.get("price")
            or dcf_data.get("currentPrice")
            or dcf_data.get("current_price")
            or dcf_data.get("market_price")
        )

        if current_price:
            upside_pct = ((fair_value - current_price) / current_price) * 100
        else:
            upside_pct = self._to_float(
                company_dto.get("upside")
                or dcf_data.get("upside")
                or dcf_data.get("upside_pct")
            )

        return fair_value, current_price, upside_pct

    def _build_prompt(
        self,
        ticker: str,
        company_name: str,
        fair_value: float,
        current_price: float,
        upside_pct: float,
        cells: List[Dict[str, Any]],
    ) -> str:
        conversation = self._conversation_text(cells)

        return f"""Create an investment thesis for {company_name} ({ticker}).

Valuation snapshot:
- Fair Value: {fair_value:.2f}
- Current Price: {current_price:.2f}
- Upside: {upside_pct:.2f}%

Conversation history:
{conversation}

Return STRICT JSON only with this schema:
{{
  "title": "string",
  "summary": "string",
  "conviction": number,
  "key_assumptions": ["string"],
  "risks": ["string"],
  "target_timeframe": "string"
}}

Rules:
- conviction must be integer 1-10
- key_assumptions must contain 3-5 items
- risks must contain 3-5 items
- summary should be concise (120-220 words)
"""

    def _conversation_text(self, cells: List[Dict[str, Any]]) -> str:
        lines: List[str] = []
        for cell in cells[-20:]:
            user_input = cell.get("user_input")
            ai_output = cell.get("ai_output") or {}
            ai_message = ""
            if isinstance(ai_output, dict):
                ai_message = ai_output.get("content") or ai_output.get("message") or ""

            if user_input:
                lines.append(f"User: {str(user_input).strip()}")
            if ai_message:
                lines.append(f"Assistant: {str(ai_message).strip()[:400]}")

        return "\n".join(lines) or "No prior discussion."

    def _parse_response(
        self,
        raw: str,
        fair_value: float,
        current_price: float,
        upside_pct: float,
        ticker: str,
    ) -> ThesisPreview:
        try:
            match = re.search(r"\{[\s\S]*\}", raw)
            payload = json.loads(match.group(0) if match else raw)

            return ThesisPreview(
                title=str(payload.get("title") or f"{ticker} Investment Thesis"),
                summary=str(payload.get("summary") or ""),
                conviction=max(1, min(10, int(payload.get("conviction", 5)))),
                key_assumptions=[str(x) for x in (payload.get("key_assumptions") or [])][:5],
                risks=[str(x) for x in (payload.get("risks") or [])][:5],
                fair_value=round(fair_value, 2),
                current_price=round(current_price, 2),
                upside_pct=round(upside_pct, 2),
                target_timeframe=str(payload.get("target_timeframe") or "12-18 months"),
            )
        except Exception as exc:
            logger.warning("Failed to parse thesis JSON; using fallback. error=%s", exc)
            return self._fallback_preview(ticker, ticker, fair_value, current_price, upside_pct)

    def _fallback_preview(
        self,
        ticker: str,
        company_name: str,
        fair_value: float,
        current_price: float,
        upside_pct: float,
    ) -> ThesisPreview:
        stance = "undervalued" if upside_pct > 10 else "overvalued" if upside_pct < -10 else "fairly valued"
        return ThesisPreview(
            title=f"{ticker} Thesis: {stance.title()} Setup",
            summary=(
                f"Based on the current notebook analysis, {company_name} appears {stance}. "
                f"Our working fair value is {fair_value:.2f} versus market price {current_price:.2f}, "
                f"implying {upside_pct:.2f}% potential repricing. The thesis depends on execution against "
                "core growth and margin assumptions discussed in this session."
            ),
            conviction=6,
            key_assumptions=[
                "Revenue growth remains near modeled trajectory",
                "Margins trend toward scenario targets",
                "Cost of capital assumptions remain directionally valid",
            ],
            risks=[
                "Macro/interest-rate repricing",
                "Execution misses vs. projected fundamentals",
                "Multiple compression despite operating performance",
            ],
            fair_value=round(fair_value, 2),
            current_price=round(current_price, 2),
            upside_pct=round(upside_pct, 2),
            target_timeframe="12-18 months",
        )

    def _to_float(self, value: Any) -> float:
        try:
            if value is None:
                return 0.0
            return float(value)
        except Exception:
            return 0.0


_thesis_generator: Optional[ThesisGenerator] = None


def get_thesis_generator() -> ThesisGenerator:
    global _thesis_generator
    if _thesis_generator is None:
        _thesis_generator = ThesisGenerator()
    return _thesis_generator
