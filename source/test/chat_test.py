import requests

url = "http://localhost:8080/llm"
user_message = input("type in whatever you want: ")
data = {"query": user_message}
response = requests.post(url, json=data).json()
print(response['message'])
