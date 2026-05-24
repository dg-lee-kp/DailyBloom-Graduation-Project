FROM ollama/ollama:latest

WORKDIR /app
COPY . .

RUN chmod +x ./init.sh
ENTRYPOINT ["sh", "./init.sh"]
