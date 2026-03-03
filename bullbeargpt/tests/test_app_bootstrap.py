def test_create_app_health_endpoint():
    from app import create_app

    app = create_app()
    client = app.test_client()
    response = client.get("/health")

    assert response.status_code == 200
    assert response.get_json()["service"] == "bullbeargpt-api"


def test_cors_preflight_for_notebook_sessions():
    from app import create_app

    app = create_app()
    client = app.test_client()

    response = client.options(
        "/bullbeargpt/api/notebook/sessions",
        headers={
            "Origin": "http://localhost:4200",
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "Content-Type",
        },
    )

    assert response.status_code in (200, 204)
    assert response.headers.get("Access-Control-Allow-Origin") == "http://localhost:4200"
