# Troubleshooting

Use `stockvaluation.explain_failure` for structured failures before giving advice.

## Missing Local Service

Tell the user to run:

```bash
sv service start
```

Then retry `stockvaluation.health`.

## Missing Configuration

Tell the user to run:

```bash
sv check-env
```

Do not ask the user to paste secrets into chat. Ask them to set missing values locally.

## Non-JSON Service Response

Tell the user to run:

```bash
sv service status
```

The local service may be down, still booting, or returning an error page.

## Unsupported Company

Explain that governed support is not available for that company type. Do not invent a valuation.

## Insufficient Financial Data

Explain which data appears missing when the tool says so. Do not fill gaps manually.

## Recalculation Rejected

Use only supported override keys from `stockvaluation.recalculate`. Ask the user to restate the scenario using those fields.
