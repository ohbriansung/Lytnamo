import sys
import requests
import unittest
import argparse
import json

class TestServer(unittest.TestCase):

    def __init__(self, testname, address, key, op, item, version):
        super(TestServer, self).__init__(testname)
        self.address = address
        self.key = key
        self.data = {"op": op, "item": item, "version": json.loads(version)}

    def test_put(self):
        url = "http://" + self.address + "/put/" + self.key
        r = requests.post(url, json=self.data)
        print("sent to " + url)
        print("request body:")
        print(json.dumps(self.data, indent=4, sort_keys=True))
        print("status code:" + str(r.status_code))
        if r.status_code != 200:
            print("response body:")
            print(json.dumps(r.json(), indent=4, sort_keys=True))
        print("------------------------------------------------------")

args = None

if __name__ == "__main__":
    if len(sys.argv) < 6:
        print ("usage: python3 test_write.py <address> <key> <op> <item> <version>")
        sys.exit()

    address = sys.argv[1]
    key = sys.argv[2]
    op = sys.argv[3]
    item = sys.argv[4]
    version = sys.argv[5]
    suite = unittest.TestSuite()

    suite.addTest(TestServer("test_put", address, key, op, item, version))
    
    print("------------------------------------------------------")
    unittest.TextTestRunner().run(suite)

