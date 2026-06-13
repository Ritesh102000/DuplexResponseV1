FROM python:3.12.8-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --retries=12 CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8081/health', timeout=2)" || exit 1
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8081"]
