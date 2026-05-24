"""Minimal stdio JSON-RPC MCP server for StockValuation.io."""

from __future__ import annotations

import json
import sys
from typing import Any, TextIO

from . import __version__
from .mcp_tools import MCPToolRegistry, SUPPORTED_PROTOCOL_VERSIONS


class MCPJSONRPCServer:
    def __init__(self, registry: MCPToolRegistry | None = None):
        self.registry = registry or MCPToolRegistry()

    def handle(self, message: dict[str, Any]) -> dict[str, Any] | None:
        method = message.get("method")
        request_id = message.get("id")
        if request_id is None:
            self._handle_notification(method)
            return None
        try:
            if method == "initialize":
                requested_version = (message.get("params") or {}).get("protocolVersion")
                result = {
                    "protocolVersion": negotiate_protocol_version(requested_version),
                    "capabilities": {"tools": {"listChanged": False}},
                    "serverInfo": {"name": "stockvaluation-agent-native", "version": __version__},
                }
            elif method == "ping":
                result = {}
            elif method == "tools/list":
                result = {"tools": self.registry.list_tools()}
            elif method == "tools/call":
                params = message.get("params") or {}
                result = self.registry.call(params.get("name", ""), params.get("arguments") or {})
            else:
                return self._error(request_id, -32601, f"Method not found: {method}")
            return {"jsonrpc": "2.0", "id": request_id, "result": result}
        except Exception as exc:
            return self._error(request_id, -32603, str(exc))

    @staticmethod
    def _handle_notification(method: Any) -> None:
        if method in {"notifications/initialized", "$/cancelRequest"}:
            return

    @staticmethod
    def _error(request_id: Any, code: int, message: str) -> dict[str, Any]:
        return {"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}}


def negotiate_protocol_version(requested_version: Any) -> str:
    if isinstance(requested_version, str) and requested_version in SUPPORTED_PROTOCOL_VERSIONS:
        return requested_version
    return SUPPORTED_PROTOCOL_VERSIONS[0]


def serve(input_stream: TextIO = sys.stdin, output_stream: TextIO = sys.stdout) -> None:
    server = MCPJSONRPCServer()
    for line in input_stream:
        stripped = line.strip()
        if not stripped:
            continue
        try:
            message = json.loads(stripped)
        except json.JSONDecodeError:
            response = {"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": "Parse error"}}
        else:
            response = server.handle(message)
        if response is not None:
            output_stream.write(json.dumps(response, separators=(",", ":")) + "\n")
            output_stream.flush()


def main() -> None:
    serve()


if __name__ == "__main__":
    main()
