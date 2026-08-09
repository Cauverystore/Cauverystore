FROM python:3.12-slim

WORKDIR /app

ENV PYTHONUNBUFFERED=1

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY config.py gst_master_importer.py flask_api.py ./
COPY data/ ./data/

EXPOSE 5001

CMD ["python", "flask_api.py"]
