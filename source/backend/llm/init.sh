#!/bin/sh

ollama serve &
OLLAMA_PID=$!

echo "Waiting for Ollama..."
until ollama list > /dev/null 2>&1; do
	sleep 1
done

ollama pull gemma4:latest
ollama run gemma4:latest ""
wait $OLLAMA_PID
