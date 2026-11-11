"""Operator-only commissioning: print secrets once; never commit or log the output."""
import argparse
import json
from app import Database, provision_device

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--database', required=True)
    parser.add_argument('--name', required=True)
    parser.add_argument('--capability', action='append', default=[])
    args = parser.parse_args()
    print(json.dumps(provision_device(Database(args.database), args.name, args.capability), indent=2))
