FROM python:3.12-slim

WORKDIR /app
ENV PYTHONUNBUFFERED=1

# Standard library only: nothing to pip install.
COPY agents/ ./agents/

# Railway runs this on the schedule in railway.json, then the container exits.
CMD ["python", "-m", "agents", "run"]
