"""Application entrypoint for local runs and Docker."""

from api.app import StockValuationApp


if __name__ == "__main__":
    app = StockValuationApp()
    app.run()
