import requests
from Crypto.PublicKey import RSA
from Crypto.Cipher import PKCS1_v1_5 as Cipher_PKCS1_v1_5

pubkey = requests.get("http://localhost:8080/auth/pubkey").text
rsa_pub = RSA.importKey(pubkey)
cipher = Cipher_PKCS1_v1_5.new(rsa_pub)
cipher_text = cipher.encrypt("64132455 odl36d5n".encode())
res = requests.post("http://localhost:8080/auth/login?userId=abc", data=cipher_text, headers={'Content-Type': 'application/octet-stream'}).json()
print(res[0])

