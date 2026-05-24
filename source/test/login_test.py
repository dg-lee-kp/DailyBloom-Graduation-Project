import requests

url = "http://localhost:8080/login"
email = input("enter your email to login: ")
data = {"login_info": email}
response = requests.post(url, json=data).json()
if (response['status'] == 1):
    print(response['message'])
else:
    print(f'user \"{response['nickname']}\" (id: {response['id']}) identified')
