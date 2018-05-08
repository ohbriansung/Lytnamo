import sys
import requests
import unittest
import argparse
import json

class TestServer(unittest.TestCase):

    def __init__(self, testname, address, key):
        super(TestServer, self).__init__(testname)
        self.address = address
        self.key = key

    def test_get(self):
        url = "http://" + self.address + "/get/" + self.key
        r = requests.get(url)
        self.assertEqual(r.status_code, 200)
        print("sent to " + url)
        print("response body:")
        print(json.dumps(r.json(), indent=4, sort_keys=True))
        print("------------------------------------------------------")

args = None

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print ("usage: python3 test_read.py <address> <key>")
        sys.exit()

    address = sys.argv[1]
    key = sys.argv[2]
    suite = unittest.TestSuite()

    suite.addTest(TestServer("test_get", address, key))
    
    print("------------------------------------------------------")
    unittest.TextTestRunner().run(suite)

