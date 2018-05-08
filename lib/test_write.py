import sys
import requests
import unittest
import argparse
import json

class TestServer(unittest.TestCase):

    def __init__(self, testname, address, key, data):
        super(TestServer, self).__init__(testname)
        self.address = address
        self.key = key
        self.data = {"data":data}

    def test_put(self):
        url = "http://" + self.address + "/put/" + self.key
        r = requests.post(url, json=self.data)
        self.assertEqual(r.status_code, 200)
        print("sent to " + url)
        print("request body:")
        print(json.dumps(self.data, indent=4, sort_keys=True))
        print("------------------------------------------------------")

args = None

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print ("usage: python3 test_write.py <address> <key> <data>")
        sys.exit()

    address = sys.argv[1]
    key = sys.argv[2]
    data = sys.argv[3]
    suite = unittest.TestSuite()

    suite.addTest(TestServer("test_put", address, key, data))
    
    print("------------------------------------------------------")
    unittest.TextTestRunner().run(suite)

