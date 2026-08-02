FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

COPY pyproject.toml .
COPY app ./app

RUN pip install --no-cache-dir .

EXPOSE 9000

CMD ["uvicorn", "app.server:app", "--host", "0.0.0.0", "--port", "9000"]
