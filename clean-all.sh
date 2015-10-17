#!/bin/sh

sbt clean
rm -r .idea target project/target project/project 2>/dev/null || true
