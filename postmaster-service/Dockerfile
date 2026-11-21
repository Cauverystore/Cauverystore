FROM python:3.12-slim

WORKDIR /app
ENV PYTHONUNBUFFERED=1

# Standard library only: nothing to pip install.
COPY postmaster/ ./postmaster/

# Railway runs this on a schedule, then the container exits.
CMD ["python", "-m", "postmaster"]
