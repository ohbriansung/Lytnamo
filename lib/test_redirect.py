import sys
import requests
import unittest
import argparse
import json

class TestServer(unittest.TestCase):

    def __init__(self, testname, address, hashKey, key):
        super(TestServer, self).__init__(testname)
        self.address = address
        self.hashKey = hashKey
        self.key = key

    def test_redirect(self):
        url = "http://" + self.address + "/get/" + self.hashKey + "/" + self.key
        r = requests.get(url)
        self.assertEqual(r.status_code, 200)
        print("sent to " + url)
        print("response body:")
        print(json.dumps(r.json(), indent=4, sort_keys=True))
        print("------------------------------------------------------")

args = None

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print ("usage: python3 test_redirect.py <address> <hashKey> <key>")
        sys.exit()

    address = sys.argv[1]
    hashKey = sys.argv[2]
    key = sys.argv[3]
    suite = unittest.TestSuite()

    suite.addTest(TestServer("test_redirect", address, hashKey, key))
    
    print("------------------------------------------------------")
    unittest.TextTestRunner().run(suite)

