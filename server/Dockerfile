FROM python:3.12-slim

WORKDIR /srv
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
# Shipped deliberately: running the evals inside the image means they exercise the exact prompt
# and schema that is deployed, not a copy. Nothing at runtime imports them.
COPY evals ./evals

# Non-root: the container holds an Anthropic API key, so a compromised process should not be root.
RUN useradd --create-home --uid 10001 hisabak && chown -R hisabak /srv
USER hisabak

EXPOSE 8080
# --proxy-headers so the rate limiter sees the real client IP behind a reverse proxy.
# --no-access-log: access logs would record request lines; bodies carry SMS text.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080", "--proxy-headers", "--no-access-log"]
